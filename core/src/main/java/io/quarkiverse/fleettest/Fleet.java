package io.quarkiverse.fleettest;

import java.net.URI;
import java.util.List;

/**
 * The running instances and the load balancer in front of them.
 * <p>
 * The load balancer works at the TCP level, like a Kubernetes {@code Service}: it chooses an instance when a
 * connection is opened, and the connection then stays with that instance. A client that opens a connection per
 * request, as RestAssured does by default, is therefore balanced per request; a client that keeps connections
 * alive sticks to one instance, exactly as it would in production.
 */
public interface Fleet {

    /**
     * The address of the load balancer.
     */
    URI url();

    int size();

    FleetInstance instance(int index);

    List<FleetInstance> instances();

    /**
     * The instances the load balancer currently routes new connections to. With readiness off, every running
     * instance; with it on, the ready ones.
     */
    List<FleetInstance> readyInstances();

    /**
     * Routes every new connection to the given instance, until {@link #roundRobin()} is called. Connections
     * already open are not affected. Reset to round-robin after each test.
     */
    void pin(int index);

    /**
     * Routes each new connection to the next ready instance. This is the default.
     */
    void roundRobin();

    /**
     * The state of the fleet in a few lines: every instance with its port, whether it runs and is ready, how
     * many connections it served, and where its log is. Printed when a test fails.
     */
    String describe();
}
