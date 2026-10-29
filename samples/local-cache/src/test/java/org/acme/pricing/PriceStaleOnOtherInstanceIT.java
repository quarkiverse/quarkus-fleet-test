package org.acme.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

/**
 * The bug: a cache inside the process is one cache per instance. {@code @CacheInvalidate} evicts the entry of
 * the instance that handled the write and of no other, so an instance that has the old price cached keeps
 * serving it, for as long as the entry lives: here forever, there is no expiry. On one instance a write is
 * always followed by fresh reads, which is why a {@code @QuarkusTest} never sees this.
 */
@QuarkusFleetTest(instances = 2)
class PriceStaleOnOtherInstanceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theOtherInstanceKeepsServingTheOldPrice() {
        // the first instance reads the price and caches it
        assertThat(Prices.priceThrough(fleet, 0)).isEqualTo(10);

        // the second instance changes it, and sees the change itself
        Prices.changeThrough(fleet, 1, 20);
        assertThat(Prices.priceThrough(fleet, 1)).isEqualTo(20);

        // the first instance was never told
        assertThat(Prices.priceThrough(fleet, 0)).isEqualTo(10);
    }
}
