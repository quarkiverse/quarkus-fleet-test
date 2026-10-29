package org.acme.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The fix: the instance that handles the write tells the others, over a Redis pub/sub channel, and each of them
 * evicts its own entry. The message takes a moment to arrive, so the other instance is fresh shortly after the
 * write, not at the very instant.
 */
@QuarkusFleetTest(instances = 2)
@TestProfile(FleetWideInvalidationProfile.class)
class PriceFreshOnOtherInstanceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theOtherInstanceServesTheNewPrice() {
        assertThat(Prices.priceThrough(fleet, 0)).isEqualTo(10);

        Prices.changeThrough(fleet, 1, 20);
        assertThat(Prices.priceThrough(fleet, 1)).isEqualTo(20);

        await().atMost(Duration.ofSeconds(10)).until(() -> Prices.priceThrough(fleet, 0) == 20);
    }
}
