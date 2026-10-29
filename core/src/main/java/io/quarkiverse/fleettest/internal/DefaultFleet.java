package io.quarkiverse.fleettest.internal;

import java.io.Closeable;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.FleetInstance;

final class DefaultFleet implements Fleet, Closeable {

    private final List<DefaultFleetInstance> instances;
    private final Timeline timeline = new Timeline();
    private final TcpLoadBalancer loadBalancer;

    DefaultFleet(List<DefaultFleetInstance> instances, ReadinessSettings readiness) {
        this.instances = List.copyOf(instances);
        this.loadBalancer = new TcpLoadBalancer(instances.size(), readiness, timeline);
        for (DefaultFleetInstance instance : this.instances) {
            instance.attach(loadBalancer.backend(instance.index()), timeline);
        }
    }

    @Override
    public URI url() {
        return URI.create("http://localhost:" + loadBalancer.port());
    }

    int port() {
        return loadBalancer.port();
    }

    @Override
    public int size() {
        return instances.size();
    }

    @Override
    public FleetInstance instance(int index) {
        return instances.get(index);
    }

    @Override
    public List<FleetInstance> instances() {
        return List.copyOf(instances);
    }

    @Override
    public List<FleetInstance> readyInstances() {
        return List.copyOf(instances.stream().filter(DefaultFleetInstance::isReady).toList());
    }

    @Override
    public void pin(int index) {
        loadBalancer.pin(index);
    }

    @Override
    public void roundRobin() {
        loadBalancer.roundRobin();
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("Fleet of ").append(instances.size()).append(" at ").append(url()).append(", ")
                .append(loadBalancer.describeRouting());
        long refused = loadBalancer.refusedConnections();
        if (refused > 0) {
            sb.append(", ").append(refused).append(" connection(s) refused (no instance to route to)");
        }
        sb.append('\n');
        for (DefaultFleetInstance instance : instances) {
            sb.append("  instance ").append(instance.index()).append(": ");
            if (instance.isRunning()) {
                sb.append(instance.isPaused() ? "PAUSED on port " : "running on port ").append(instance.port())
                        .append(instance.isReady() ? ", ready" : ", NOT ready");
            } else {
                sb.append("stopped");
            }
            sb.append(", readiness transitions ").append(instance.readinessTransitions())
                    .append(", served ").append(loadBalancer.backend(instance.index()).connections())
                    .append(" connection(s)")
                    .append(", log: ").append(instance.logFile()).append('\n');
        }
        return sb.toString();
    }

    // What the balancer and the instances did during the current test.
    Timeline timeline() {
        return timeline;
    }

    // Back to the state a test expects: nothing paused, round-robin. Also forgets the timeline and the counts, so
    // the next test starts with empty ones.
    void reset() {
        for (DefaultFleetInstance instance : instances) {
            instance.resume();
        }
        loadBalancer.roundRobin();
        loadBalancer.clearCounts();
        timeline.clear();
    }

    // Every instance is stopped whatever the others did, then the balancer is closed.
    @Override
    public void close() {
        Closeables.closeAll(null, Stream.concat(instances.stream(), Stream.of(loadBalancer)).toArray(AutoCloseable[]::new));
    }
}
