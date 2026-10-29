package io.quarkiverse.fleettest.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkiverse.fleettest.RawHttp;
import io.quarkiverse.fleettest.Readiness;

// An AWS ALB or Envoy in panic mode routes to every instance when none is ready, rather than to none.
@QuarkusFleetTest(instances = 2, readiness = @Readiness(path = "/q/health/ready", intervalMillis = 100, failureThreshold = 2, failOpen = true))
class FailOpenIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void withNoReadyInstanceEveryInstanceIsRoutedTo() throws Exception {
        Instances.setAllReady(fleet, false);
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> fleet.readyInstances().isEmpty());
            assertThat(RawHttp.get(fleet.url(), "/instance")).isEqualTo(200);
            assertThat(RawHttp.get(fleet.url(), "/instance")).isEqualTo(200);
        } finally {
            Instances.setAllReady(fleet, true);
        }
    }
}
