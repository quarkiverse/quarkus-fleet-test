package io.quarkiverse.fleettest.internal;

import static io.quarkiverse.fleettest.internal.TestSupport.READINESS_OFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.test.common.ArtifactLauncher;

class CloseablesTest {

    @Test
    void closeAllClosesTheRestEvenWhenOneFailsToClose() {
        FakeLauncher launcher = new FakeLauncher(() -> FakeLauncher.listening(1), new IOException("would not stop"));
        DefaultFleet fleet = new DefaultFleet(List.of(TestSupport.instance(0, launcher)), READINESS_OFF);
        fleet.instance(0).start();
        AtomicBoolean devServicesClosed = new AtomicBoolean();
        ArtifactLauncher.InitContext.DevServicesLaunchResult devServices = new ArtifactLauncher.InitContext.DevServicesLaunchResult() {
            @Override
            public Map<String, String> properties() {
                return Map.of();
            }

            @Override
            public String networkId() {
                return null;
            }

            @Override
            public boolean manageNetwork() {
                return false;
            }

            @Override
            public CuratedApplication getCuratedApplication() {
                return null;
            }

            @Override
            public void close() {
                devServicesClosed.set(true);
            }
        };

        assertThatThrownBy(() -> Closeables.closeAll(null, fleet, null, devServices))
                .isInstanceOf(RuntimeException.class);

        assertThat(launcher.closed).isTrue();
        assertThat(devServicesClosed).isTrue();
    }
}
