package io.quarkiverse.fleettest.internal;

import java.io.Closeable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;

/**
 * A TCP proxy in front of the instances. It picks an instance per connection, like kube-proxy does for a
 * {@code Service}, and pipes bytes in both directions without looking at them, so any protocol the application
 * speaks (HTTP/1.1, HTTP/2, gRPC, WebSockets, TLS) goes through unchanged.
 * <p>
 * With readiness on, it polls each instance like a kubelet, with thresholds, and stops routing
 * to an unready or stopped instance only after the deregistration lag, like an endpoint change propagating to
 * kube-proxy.
 */
final class TcpLoadBalancer implements Closeable {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    private final Vertx vertx;
    private final NetClient client;
    private final NetServer server;
    private final List<Backend> backends;
    private final ReadinessSettings readiness;
    private final ScheduledExecutorService prober;
    private final HttpClient probeClient;
    private final AtomicInteger next = new AtomicInteger();
    private volatile int pinned = -1;
    private final int port;
    private final Timeline timeline;
    private final AtomicLong refusedConnections = new AtomicLong();

    TcpLoadBalancer(int size, ReadinessSettings readiness, Timeline timeline) {
        this.readiness = readiness;
        this.timeline = timeline;
        this.backends = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            backends.add(new Backend(i));
        }
        this.vertx = Vertx.vertx();
        // Defaults are right for a byte relay: TCP_NODELAY on (no Nagle batching of relayed buffers), no idle
        // timeout (long-lived WebSocket, SSE and gRPC streams must not be cut), and no silent reconnect. The one
        // default worth bounding is the 60s connect timeout: a connection the balancer opens to an instance that
        // is unreachable rather than refused should fail in bounded time, as a real dropped endpoint would.
        this.client = vertx.createNetClient(new NetClientOptions().setConnectTimeout(CONNECT_TIMEOUT_MILLIS));
        this.server = vertx.createNetServer().connectHandler(this::onConnection);
        this.port = server.listen(0, "localhost").await().actualPort();
        if (readiness.enabled()) {
            this.probeClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(readiness.intervalMillis())).build();
            this.prober = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "fleet-readiness-prober");
                t.setDaemon(true);
                return t;
            });
            this.prober.scheduleAtFixedRate(this::probeAll, 0, readiness.intervalMillis(), TimeUnit.MILLISECONDS);
        } else {
            this.probeClient = null;
            this.prober = null;
        }
    }

    int port() {
        return port;
    }

    void pin(int index) {
        backend(index);
        pinned = index;
        timeline.record("routing pinned to instance " + index);
    }

    void roundRobin() {
        if (pinned >= 0) {
            timeline.record("routing back to round-robin");
        }
        pinned = -1;
    }

    long refusedConnections() {
        return refusedConnections.get();
    }

    // Forgets the per-test counts (connections, readiness transitions), called after each test.
    void clearCounts() {
        refusedConnections.set(0);
        for (Backend b : backends) {
            b.connections.set(0);
            b.transitions.set(0);
        }
    }

    String describeRouting() {
        int pin = pinned;
        return pin >= 0 ? "pinned to instance " + pin : "round-robin";
    }

    Backend backend(int index) {
        if (index < 0 || index >= backends.size()) {
            throw new IllegalArgumentException("No instance with index " + index + ", the fleet has " + backends.size());
        }
        return backends.get(index);
    }

    private void probeAll() {
        for (Backend backend : backends) {
            // the generation before the port: should the process change in between, the probe goes to the new
            // port under the old generation and its verdict is discarded, which is the safe way round
            int generation = backend.generation;
            int port = backend.port;
            if (port <= 0) {
                continue;
            }
            boolean success;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + readiness.path()))
                        .timeout(Duration.ofMillis(readiness.intervalMillis())).GET().build();
                int status = probeClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                success = status >= 200 && status < 300;
            } catch (Exception e) {
                success = false;
            }
            backend.probed(generation, success);
        }
    }

    private void onConnection(NetSocket downstream) {
        // hold incoming bytes until the upstream connection exists, otherwise they would be dropped
        downstream.pause();
        Backend backend = choose();
        if (backend == null) {
            // nothing to route to: refuse, as kube-proxy does for a Service without ready endpoints
            refusedConnections.incrementAndGet();
            downstream.close();
            return;
        }
        backend.connections.incrementAndGet();
        // during the deregistration lag after a stop this is the old port: the connection fails, as it would
        client.connect(backend.portToConnect(), "localhost")
                .onSuccess(upstream -> {
                    downstream.closeHandler(v -> upstream.close());
                    upstream.closeHandler(v -> downstream.close());
                    relay(downstream, upstream);
                    relay(upstream, downstream);
                    downstream.resume();
                })
                .onFailure(t -> downstream.close());
    }

    // Copies bytes from one socket to the other, with backpressure.
    private void relay(NetSocket from, NetSocket to) {
        from.handler(buffer -> {
            to.write(buffer);
            if (to.writeQueueFull()) {
                from.pause();
                to.drainHandler(v -> from.resume());
            }
        });
        from.endHandler(v -> to.close());
    }

    // The instance to connect to, or null when there is none.
    private Backend choose() {
        long now = System.currentTimeMillis();
        int pin = pinned;
        if (pin >= 0) {
            Backend backend = backends.get(pin);
            return backend.routable(now) ? backend : null;
        }
        Backend chosen = nextRoutable(now, false);
        if (chosen == null && readiness.failOpen()) {
            // no ready instance: an ALB or Envoy in panic mode routes to all of them rather than to none
            chosen = nextRoutable(now, true);
        }
        return chosen;
    }

    private Backend nextRoutable(long now, boolean ignoreReadiness) {
        for (int attempt = 0; attempt < backends.size(); attempt++) {
            Backend backend = backends.get(Math.floorMod(next.getAndIncrement(), backends.size()));
            if (ignoreReadiness ? backend.port > 0 : backend.routable(now)) {
                return backend;
            }
        }
        return null;
    }

    @Override
    public void close() {
        if (prober != null) {
            prober.shutdownNow();
        }
        // stop accepting, then close outbound connections, then the Vertx itself: the Vertx would close the
        // server and client through its own hooks, but closing them explicitly is clearer and does not rely on it
        server.close().await();
        client.close().await();
        vertx.close().await();
    }

    // One instance as the balancer sees it: its port, its readiness, and its open connections. The instance
    // tells it when its process starts and stops; it is the one place that knows whether the instance is running.
    final class Backend {

        private final int index;
        private volatile int port = -1;
        // The port of the process that stopped, routed to during the deregistration lag.
        private volatile int previousPort = -1;
        private volatile boolean ready;
        // When the balancer stops routing to this instance: never while it is ready; an unready verdict or a
        // stop, plus the lag, otherwise. An instance that has not started yet is not routed to.
        private volatile long unroutableAt = 0;
        // Counts the processes this backend has seen, so that a probe of one does not speak for the next.
        private volatile int generation;
        private int consecutiveFailures;
        private int consecutiveSuccesses;
        // Whether the current process has been ready once: its first ready verdict is a start, not a flap.
        private boolean everReady;
        private final AtomicInteger transitions = new AtomicInteger();
        private final AtomicLong connections = new AtomicLong();

        Backend(int index) {
            this.index = index;
        }

        boolean isRunning() {
            return port > 0;
        }

        int port() {
            return port;
        }

        boolean isReady() {
            return ready;
        }

        int readinessTransitions() {
            return transitions.get();
        }

        long connections() {
            return connections.get();
        }

        // The instance's process started on this port, or stopped (-1).
        synchronized void portChanged(int newPort) {
            generation++;
            if (newPort > 0) {
                port = newPort;
                previousPort = -1;
                consecutiveFailures = 0;
                consecutiveSuccesses = 0;
                everReady = false;
                // with readiness on, a new instance gets traffic once the probe says so, as a new pod does
                ready = !readiness.enabled();
                unroutableAt = ready ? Long.MAX_VALUE : 0;
            } else {
                previousPort = port;
                port = -1;
                ready = false;
                unroutableAt = System.currentTimeMillis() + readiness.deregistrationLagMillis();
            }
        }

        // Blocks until the instance is ready, polling it; used after a start so that a test never races the probe.
        void awaitReady(Duration timeout) {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (!ready) {
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException("Instance " + index + " did not become ready within " + timeout
                            + " (polling " + readiness.path() + " on port " + port + ")");
                }
                try {
                    Thread.sleep(Math.min(50, readiness.intervalMillis()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        }

        synchronized void probed(int probedGeneration, boolean success) {
            if (probedGeneration != generation) {
                // the process the probe went to was stopped, or replaced, while the probe was in flight (a
                // shutdown delay keeps the old port alive for a while): its verdict says nothing about what is
                // there now, and must not resurrect a stopped instance, mark an unprobed replacement ready, or
                // clobber the deregistration deadline that stop() armed
                return;
            }
            if (success) {
                consecutiveFailures = 0;
                consecutiveSuccesses++;
                if (!ready && consecutiveSuccesses >= readiness.successThreshold()) {
                    ready = true;
                    unroutableAt = Long.MAX_VALUE;
                    if (everReady) {
                        transitions.incrementAndGet();
                        timeline.record("instance " + index + " became ready again");
                    }
                    everReady = true;
                }
            } else {
                consecutiveSuccesses = 0;
                consecutiveFailures++;
                if (ready && consecutiveFailures >= readiness.failureThreshold()) {
                    ready = false;
                    unroutableAt = System.currentTimeMillis() + readiness.deregistrationLagMillis();
                    transitions.incrementAndGet();
                    timeline.record("instance " + index + " became unready (probe "
                            + readiness.path() + " failed " + consecutiveFailures + " times)");
                }
            }
        }

        // Ready, or unready or stopped for less than the lag.
        boolean routable(long now) {
            return now < unroutableAt;
        }

        int portToConnect() {
            int current = port;
            return current > 0 ? current : previousPort;
        }
    }
}
