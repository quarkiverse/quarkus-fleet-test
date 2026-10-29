package org.acme.quotes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

/**
 * The bug: {@code @RateLimit(5)} on the resource method counts in the JVM it runs in. Each instance lets the same
 * client through five times, so behind a load balancer the limit meant for the service is five times the number of
 * instances. On one instance the sixth request is refused exactly as documented, which is why a {@code @QuarkusTest}
 * never sees this.
 */
@QuarkusFleetTest(instances = 2)
class LimitMultipliedByInstancesIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theOtherInstanceGrantsTheLimitAgain() {
        fleet.pin(0);
        for (int i = 0; i < 5; i++) {
            assertThat(Quotes.quote().statusCode()).isEqualTo(200);
        }
        // this instance has counted five: the limit works, as far as it can see
        assertThat(Quotes.quote().statusCode()).isEqualTo(429);

        // the other instance has counted nothing
        fleet.pin(1);
        for (int i = 0; i < 5; i++) {
            assertThat(Quotes.quote().statusCode()).isEqualTo(200);
        }
    }
}
