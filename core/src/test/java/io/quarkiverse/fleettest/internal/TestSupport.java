package io.quarkiverse.fleettest.internal;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;

final class TestSupport {

    static final ReadinessSettings READINESS_OFF = new ReadinessSettings("", 250, 3, 1, 0, false);

    private TestSupport() {
    }

    static DefaultFleetInstance instance(int index, FakeLauncher launcher) {
        return new DefaultFleetInstance(index, () -> launcher, Map.of(), Duration.ofSeconds(1), false);
    }

    // A port nothing listens on, so that connecting to it fails.
    static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
