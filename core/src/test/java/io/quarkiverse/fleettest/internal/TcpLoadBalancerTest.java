package io.quarkiverse.fleettest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class TcpLoadBalancerTest {

    private final List<HttpServer> servers = new ArrayList<>();
    private TcpLoadBalancer balancer;

    @AfterEach
    void closeEverything() {
        if (balancer != null) {
            balancer.close();
        }
        servers.forEach(server -> server.stop(0));
    }

    // An instance as the prober sees it: something answering /ready.
    private int readyEndpoint(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/ready", handler);
        server.start();
        servers.add(server);
        return server.getAddress().getPort();
    }

    @Test
    void aProbeOfTheStoppedProcessDoesNotMarkItsReplacementReady() throws Exception {
        CountDownLatch oldProbed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        int oldPort = readyEndpoint(exchange -> {
            oldProbed.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        CountDownLatch replacementProbed = new CountDownLatch(1);
        int replacementPort = readyEndpoint(exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            replacementProbed.countDown();
        });
        // an interval long enough for the old process to hold its answer without the probe timing out
        balancer = new TcpLoadBalancer(1, new ReadinessSettings("/ready", 2000, 3, 1, 0, false), new Timeline());
        TcpLoadBalancer.Backend backend = balancer.backend(0);

        backend.portChanged(oldPort);
        assertThat(oldProbed.await(5, TimeUnit.SECONDS)).isTrue();
        // the process is replaced while its probe is still in flight, and the old one then answers "ready"
        backend.portChanged(-1);
        backend.portChanged(replacementPort);
        release.countDown();

        // the prober is single-threaded: once the replacement was probed, the old verdict was applied, if ever
        assertThat(replacementProbed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(backend.isReady()).isFalse();
    }

    @Test
    void clearCountsForgetsTheReadinessTransitions() throws Exception {
        AtomicInteger status = new AtomicInteger(200);
        int port = readyEndpoint(exchange -> {
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        balancer = new TcpLoadBalancer(1, new ReadinessSettings("/ready", 20, 1, 1, 0, false), new Timeline());
        TcpLoadBalancer.Backend backend = balancer.backend(0);
        backend.portChanged(port);
        await().atMost(Duration.ofSeconds(5)).until(backend::isReady);
        status.set(503);
        await().atMost(Duration.ofSeconds(5)).until(() -> !backend.isReady());
        assertThat(backend.readinessTransitions()).isEqualTo(1);

        balancer.clearCounts();

        assertThat(backend.readinessTransitions()).isZero();
    }
}
