package io.quarkiverse.fleettest.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.FleetInstance;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkiverse.fleettest.RawHttp;
import io.quarkiverse.fleettest.Readiness;

// A crash and a freeze, the two things that happen to a process besides a clean stop.
@QuarkusFleetTest(instances = 2, readiness = @Readiness(path = "/q/health/ready", intervalMillis = 100, failureThreshold = 2))
class LifecycleIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void aKilledInstanceIsGoneAtOnce() throws Exception {
        URI url = fleet.instance(0).url();
        fleet.instance(0).kill();
        assertThat(fleet.instance(0).isRunning()).isFalse();
        // nothing listens any more: the connection is refused, not closed after a shutdown
        assertThatThrownBy(() -> RawHttp.get(url, "/instance", Duration.ofSeconds(1))).isInstanceOf(IOException.class);
        for (int i = 0; i < 5; i++) {
            assertThat(Instances.serving()).isEqualTo("1");
        }

        fleet.instance(0).start();
        assertThat(fleet.readyInstances()).hasSize(2);
    }

    @Test
    void aPausedInstanceStopsAnsweringAndLeavesRotation() throws Exception {
        FleetInstance zero = fleet.instance(0);
        zero.pause();
        assertThat(zero.isPaused()).isTrue();
        assertThat(zero.isRunning()).isTrue();
        // the socket still accepts, since the kernel does that, but nothing in the process answers
        assertThatThrownBy(() -> RawHttp.get(zero.url(), "/instance", Duration.ofMillis(500)))
                .isInstanceOf(SocketTimeoutException.class);
        await().atMost(Duration.ofSeconds(3)).until(() -> fleet.readyInstances().size() == 1);
        for (int i = 0; i < 5; i++) {
            assertThat(Instances.serving()).isEqualTo("1");
        }

        zero.resume();
        assertThat(zero.isPaused()).isFalse();
        await().atMost(Duration.ofSeconds(3)).until(() -> fleet.readyInstances().size() == 2);
        assertThat(RawHttp.get(zero.url(), "/instance", Duration.ofSeconds(1))).isEqualTo(200);
    }
}
