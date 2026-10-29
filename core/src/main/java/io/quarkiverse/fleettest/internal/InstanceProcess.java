package io.quarkiverse.fleettest.internal;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkus.deployment.util.ContainerRuntimeUtil;
import io.smallrye.common.process.ProcessBuilder;

/**
 * The operating-system side of one instance: the process the launcher started, found among the children of
 * the test JVM by a marker on its command line, or the container that process runs. Lets a test do what the
 * launchers do not expose: kill, pause and resume.
 */
final class InstanceProcess {

    private final ProcessHandle handle;
    // null unless the instance is a container, in which case the handle is the container runtime's client
    private final String containerName;
    private final String containerRuntime;

    private InstanceProcess(ProcessHandle handle, String containerName, String containerRuntime) {
        this.handle = handle;
        this.containerName = containerName;
        this.containerRuntime = containerRuntime;
    }

    static InstanceProcess find(String marker, boolean container) {
        ProcessHandle handle = ProcessHandle.current().descendants()
                .filter(h -> h.info().commandLine().map(c -> c.contains(marker)).orElse(false))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No child process of the test JVM carries " + marker
                        + " on its command line; killing and pausing an instance needs an OS that exposes it"));
        if (!container) {
            return new InstanceProcess(handle, null, null);
        }
        List<String> args = List.of(handle.info().arguments()
                .orElseThrow(
                        () -> new IllegalStateException("The arguments of the container runtime process are not available")));
        int name = args.indexOf("--name");
        if (name < 0 || name + 1 >= args.size()) {
            throw new IllegalStateException("No --name among the container runtime's arguments: " + args);
        }
        return new InstanceProcess(handle, args.get(name + 1),
                ContainerRuntimeUtil.detectContainerRuntime(true).getExecutableName());
    }

    // SIGKILL: no shutdown hooks, no graceful anything, as when a pod is OOM-killed or a node dies.
    void kill() {
        if (containerName != null) {
            ProcessBuilder.exec(containerRuntime, "kill", containerName);
        } else {
            handle.destroyForcibly();
        }
        try {
            handle.onExit().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("The process of the instance did not exit after being killed", e);
        }
    }

    // SIGSTOP: the process stays alive and keeps its sockets and locks, but nothing in it runs.
    void pause() {
        if (containerName != null) {
            ProcessBuilder.exec(containerRuntime, "pause", containerName);
        } else {
            ProcessBuilder.exec("kill", "-STOP", Long.toString(handle.pid()));
        }
    }

    void resume() {
        if (containerName != null) {
            ProcessBuilder.exec(containerRuntime, "unpause", containerName);
        } else {
            ProcessBuilder.exec("kill", "-CONT", Long.toString(handle.pid()));
        }
    }
}
