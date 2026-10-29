package org.acme.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkiverse.fleettest.RawHttp;
import io.quarkiverse.fleettest.Readiness;
import io.quarkiverse.fleettest.Traffic;

/**
 * The fix is in the deployment, not the application: a readiness probe on {@code /q/health/ready}. The new
 * replica joins the rotation when its catalog is loaded and not before, and no request fails.
 */
@QuarkusFleetTest(instances = 2, running = 1, readiness = @Readiness(path = "/q/health/ready", intervalMillis = 100))
class NewInstanceWaitsUntilReadyIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void noRequestReachesTheInstanceBeforeItIsReady() throws Exception {
        // the first instance has finished loading; nothing the client sees is its doing
        await().atMost(Duration.ofSeconds(10)).ignoreExceptionsInstanceOf(IOException.class)
                .until(() -> RawHttp.get(fleet.instance(0).url(), "/catalog") == 200);

        // a client that never stops sending requests, and the second instance started in the middle
        Traffic.Outcome outcome = Traffic.during(fleet, "/catalog", Duration.ofSeconds(4), () -> fleet.instance(1).start());
        assertThat(outcome.succeeded()).isGreaterThan(0);
        assertThat(outcome.failed()).isZero();
    }
}
