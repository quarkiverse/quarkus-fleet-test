package io.quarkiverse.fleettest.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import io.quarkiverse.fleettest.FleetInstance;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.test.common.ArtifactLauncher;
import io.quarkus.test.common.ListeningResult;

final class DefaultFleetInstance implements FleetInstance, AutoCloseable {

    static final String INSTANCE_INDEX_PROPERTY = "fleet-test.instance-index";
    // Unique per process start; it is how the process is found again for a kill or a pause.
    static final String INSTANCE_ID_PROPERTY = "fleet-test.instance-id";

    private final int index;
    // Creates an initialised launcher; one per start, so that each process gets its own id and log file.
    private final Supplier<ArtifactLauncher<?>> launchers;
    private final Map<String, String> systemProperties;
    private final Duration startTimeout;
    private final boolean container;
    // The balancer's view of this instance: it owns the port, and with it whether the instance is running.
    private TcpLoadBalancer.Backend backend;
    private Timeline timeline;
    private ArtifactLauncher<?> launcher;
    private String instanceId;
    private InstanceProcess process;
    private volatile boolean paused;
    private Path logFile;
    private Path temporaryDirectory;

    DefaultFleetInstance(int index, Supplier<ArtifactLauncher<?>> launchers, Map<String, String> systemProperties,
            Duration startTimeout, boolean container) {
        this.index = index;
        this.launchers = launchers;
        this.systemProperties = new HashMap<>(systemProperties);
        this.systemProperties.put(INSTANCE_INDEX_PROPERTY, Integer.toString(index));
        this.startTimeout = startTimeout;
        this.container = container;
    }

    void attach(TcpLoadBalancer.Backend backend, Timeline timeline) {
        this.backend = backend;
        this.timeline = timeline;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public URI url() {
        return URI.create("http://localhost:" + port());
    }

    @Override
    public int port() {
        if (!backend.isRunning()) {
            throw new IllegalStateException("Instance " + index + " is not running");
        }
        return backend.port();
    }

    @Override
    public Path logFile() {
        return logFile;
    }

    @Override
    public boolean isRunning() {
        return backend.isRunning();
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public boolean isReady() {
        return backend.isReady();
    }

    @Override
    public int readinessTransitions() {
        return backend.readinessTransitions();
    }

    @Override
    public synchronized void start() {
        if (launcher != null) {
            throw new IllegalStateException("Instance " + index + " is already running");
        }
        ArtifactLauncher<?> newLauncher = launchers.get();
        String id = UUID.randomUUID().toString();
        Map<String, String> properties = new HashMap<>(systemProperties);
        properties.put(INSTANCE_ID_PROPERTY, id);
        Path newTemporaryDirectory = null;
        if (!container) {
            // a temporary directory of its own, as a pod has a filesystem of its own; a container has one already
            newTemporaryDirectory = createTemporaryDirectory(id);
            properties.put("java.io.tmpdir", newTemporaryDirectory.toString());
        }
        newLauncher.includeAsSysProps(properties);
        try {
            ListeningResult server = newLauncher.start().server()
                    .orElseThrow(() -> new IllegalStateException(
                            "Instance " + index + " started but no HTTP port was found in its log"));
            logFile = server.logPath();
            int newPort = server.address().port();
            backend.portChanged(newPort);
            timeline.record("instance " + index + " started on port " + newPort);
            backend.awaitReady(startTimeout);
        } catch (IOException e) {
            abandon(newLauncher, newTemporaryDirectory, e);
            throw new UncheckedIOException("Instance " + index + " failed to start", e);
        } catch (RuntimeException | Error e) {
            abandon(newLauncher, newTemporaryDirectory, e);
            throw e;
        }
        launcher = newLauncher;
        instanceId = id;
        temporaryDirectory = newTemporaryDirectory;
    }

    // Nothing of a process that failed to start stays behind: the balancer forgets its port, the process is
    // closed and its temporary directory deleted. The failure is what gets thrown; what the cleanup adds is
    // suppressed into it.
    private void abandon(ArtifactLauncher<?> failed, Path failedTemporaryDirectory, Throwable failure) {
        if (backend.isRunning()) {
            backend.portChanged(-1);
            timeline.record("instance " + index + " abandoned, it did not start");
        }
        Closeables.closeAll(failure, failed);
        deleteRecursively(failedTemporaryDirectory);
    }

    private static Path createTemporaryDirectory(String id) {
        try {
            return Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "fleet-test", id));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the instance's temporary directory", e);
        }
    }

    // Best effort: a directory left behind is not worth failing a test over.
    private static void deleteRecursively(Path directory) {
        if (directory != null) {
            IoUtils.recursiveDelete(directory);
        }
    }

    // The process is gone: forget it, and what it had.
    private void forget() {
        launcher = null;
        process = null;
        deleteRecursively(temporaryDirectory);
        temporaryDirectory = null;
    }

    @Override
    public synchronized void stop() {
        if (launcher == null) {
            return;
        }
        resume(); // a stopped process would not act on SIGTERM
        backend.portChanged(-1);
        timeline.record("instance " + index + " stopped");
        try {
            launcher.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Instance " + index + " failed to stop", e);
        } finally {
            forget();
        }
    }

    // Closing an instance stops it, so that a fleet can be closed as a whole.
    @Override
    public void close() {
        stop();
    }

    @Override
    public synchronized void kill() {
        if (launcher == null) {
            return;
        }
        resume();
        process().kill();
        backend.portChanged(-1);
        timeline.record("instance " + index + " killed");
        try {
            launcher.close(); // the process is gone; this releases what the launcher holds
        } catch (Exception ignored) {
        } finally {
            forget();
        }
    }

    @Override
    public synchronized void pause() {
        if (launcher == null) {
            throw new IllegalStateException("Instance " + index + " is not running");
        }
        if (paused) {
            return;
        }
        process().pause();
        paused = true;
        timeline.record("instance " + index + " paused");
    }

    @Override
    public synchronized void resume() {
        if (!paused) {
            return;
        }
        process().resume();
        paused = false;
        timeline.record("instance " + index + " resumed");
    }

    private InstanceProcess process() {
        if (process == null) {
            process = InstanceProcess.find(instanceId, container);
        }
        return process;
    }
}
