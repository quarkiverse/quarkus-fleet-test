package org.acme.work;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkiverse.fleettest.Readiness;
import io.quarkiverse.fleettest.Traffic;
import io.quarkus.test.junit.TestProfile;

/**
 * The fix: {@code quarkus.shutdown.delay}. The instance fails its readiness probe but keeps serving for longer
 * than the propagation delay, so every request routed to it in that window still succeeds.
 */
@QuarkusFleetTest(instances = 2, readiness = @Readiness(path = "/q/health/ready", intervalMillis = 100, failureThreshold = 1, deregistrationLagMillis = 1000))
@TestProfile(ShutdownDelayProfile.class)
class NoRequestDroppedOnRestartIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void stoppingAnInstanceFailsNoRequest() throws Exception {
        // a client that never stops sending requests, and a stop of one instance in the middle
        Traffic.Outcome outcome = Traffic.during(fleet, "/work", Duration.ofSeconds(3), () -> fleet.instance(0).stop());
        assertThat(outcome.succeeded()).isGreaterThan(0);
        assertThat(outcome.failed()).isZero();
    }
}
