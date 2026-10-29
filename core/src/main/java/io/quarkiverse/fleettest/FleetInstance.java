package io.quarkiverse.fleettest;

import java.net.URI;
import java.nio.file.Path;

/**
 * One process of the application. Its index is passed to the process as the {@code fleet-test.instance-index}
 * system property, so the application can read it with {@code @ConfigProperty} if a test needs to tell
 * instances apart in a response.
 * <p>
 * Each process has a temporary directory of its own ({@code java.io.tmpdir}), as each pod has a filesystem of
 * its own: a file one instance writes there, an upload for example, does not exist for the others.
 */
public interface FleetInstance {

    int index();

    /**
     * The address of this instance itself, bypassing the load balancer.
     */
    URI url();

    int port();

    Path logFile();

    boolean isRunning();

    /**
     * Whether the process is paused: alive, holding its sockets, locks and leases, but running nothing.
     */
    boolean isPaused();

    /**
     * Whether the load balancer considers this instance ready. Always true for a running instance when readiness
     * is off.
     */
    boolean isReady();

    /**
     * How many times the load balancer saw this instance change between ready and unready, not counting the
     * moment a started process first became ready. An instance that flaps under load shows here.
     */
    int readinessTransitions();

    /**
     * Shuts the process down gracefully, as an orchestrator would when scaling in or rolling out. With a
     * deregistration lag, the load balancer keeps routing to it for that long, as kube-proxy does.
     */
    void stop();

    /**
     * Starts a new process for this instance, with the same configuration. The port changes. With readiness on,
     * returns once the instance is ready.
     */
    void start();

    /**
     * Kills the process outright, with SIGKILL, as when a pod is OOM-killed or its node dies: no shutdown hooks run,
     * nothing is committed or released, and consumers do not leave their groups. The load balancer treats it as
     * a stop: with a deregistration lag it keeps routing to the dead port for that long, and connections are
     * refused.
     */
    void kill();

    /**
     * Freezes the process with SIGSTOP (or pauses its container). The process stays alive and keeps every
     * socket, lock and lease it holds, but runs nothing: a long GC pause, a frozen VM, a partitioned node. The
     * load balancer is not told; with readiness on, the probe times out and the instance leaves rotation. A
     * paused instance is resumed after each test.
     */
    void pause();

    /**
     * Lets a paused process run again, with everything it held and everything it was doing.
     */
    void resume();
}
