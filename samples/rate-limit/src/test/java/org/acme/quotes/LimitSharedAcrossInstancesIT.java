package org.acme.quotes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The fix: {@code quotes.limit.shared=true}, and the count per client is kept in Redis, where every instance sees
 * it. Five requests through one instance use up the limit for the whole service, so the sixth is refused whichever
 * instance it lands on.
 */
@QuarkusFleetTest(instances = 2)
@TestProfile(SharedLimitProfile.class)
class LimitSharedAcrossInstancesIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theOtherInstanceRefusesTheSixthRequest() {
        fleet.pin(0);
        for (int i = 0; i < 5; i++) {
            assertThat(Quotes.quote().statusCode()).isEqualTo(200);
        }

        fleet.pin(1);
        assertThat(Quotes.quote().statusCode()).isEqualTo(429);
    }
}
