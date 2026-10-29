package io.quarkiverse.fleettest.internal;

import static io.quarkiverse.fleettest.internal.TestSupport.READINESS_OFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.ListeningResults;

class DefaultFleetInstanceTest {

    private TcpLoadBalancer balancer;

    @AfterEach
    void closeBalancer() {
        if (balancer != null) {
            balancer.close();
        }
    }

    private DefaultFleetInstance instance(FakeLauncher launcher, ReadinessSettings readiness, Duration startTimeout) {
        Timeline timeline = new Timeline();
        balancer = new TcpLoadBalancer(1, readiness, timeline);
        DefaultFleetInstance instance = new DefaultFleetInstance(0, () -> launcher, Map.of(), startTimeout, false);
        instance.attach(balancer.backend(0), timeline);
        return instance;
    }

    @Test
    void aLauncherThatFailsToStartIsClosed() {
        FakeLauncher launcher = new FakeLauncher(() -> {
            throw new IllegalStateException("the process died");
        }, null);
        DefaultFleetInstance instance = instance(launcher, READINESS_OFF, Duration.ofSeconds(1));

        assertThatThrownBy(instance::start).isInstanceOf(IllegalStateException.class).hasMessage("the process died");

        assertThat(launcher.closed).isTrue();
        assertThat(instance.isRunning()).isFalse();
        // nothing of the failed start is left behind: the next start is a start, not "already running"
        assertThatThrownBy(instance::start).hasMessage("the process died");
    }

    @Test
    void aLauncherWhoseProcessReportsNoPortIsClosed() {
        FakeLauncher launcher = new FakeLauncher(() -> ListeningResults.EMPTY, null);
        DefaultFleetInstance instance = instance(launcher, READINESS_OFF, Duration.ofSeconds(1));

        assertThatThrownBy(instance::start).isInstanceOf(IllegalStateException.class).hasMessageContaining("no HTTP port");

        assertThat(launcher.closed).isTrue();
        assertThat(instance.isRunning()).isFalse();
    }

    @Test
    void aLauncherWhoseProcessNeverBecomesReadyIsClosed() throws IOException {
        // nothing listens on the port, so every probe fails
        FakeLauncher launcher = FakeLauncher.listeningOn(TestSupport.freePort());
        ReadinessSettings readiness = new ReadinessSettings("/ready", 20, 3, 1, 0, false);
        DefaultFleetInstance instance = instance(launcher, readiness, Duration.ofMillis(200));

        assertThatThrownBy(instance::start).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not become ready");

        assertThat(launcher.closed).isTrue();
        assertThat(instance.isRunning()).isFalse();
    }

    @Test
    void theTemporaryDirectoryGoesWithTheProcess() throws IOException {
        FakeLauncher launcher = FakeLauncher.listeningOn(12345);
        DefaultFleetInstance instance = instance(launcher, READINESS_OFF, Duration.ofSeconds(1));
        instance.start();
        Path temporaryDirectory = Path.of(launcher.systemProperties.get("java.io.tmpdir"));
        Files.writeString(temporaryDirectory.resolve("upload"), "left behind by the application");

        instance.stop();

        assertThat(temporaryDirectory).doesNotExist();
    }
}
