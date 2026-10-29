package org.acme.orders;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The fix: an idempotent consumer. The orders are delivered again, since that is what at-least-once means, and
 * every one of them is shipped once, because the shipment is recorded under the order id in the same database
 * before it is made.
 */
@QuarkusFleetTest(instances = 2, running = 1)
@TestProfile(IdempotentProfile.class)
class OrdersShippedOnceAfterCrashIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void ordersReceivedAgainAreNotShippedAgain() throws Exception {
        Crash.Outcome outcome = Crash.ofTheConsumerBeforeItCommits(fleet);
        assertThat(outcome.received()).isGreaterThan(Crash.ORDERS);
        assertThat(outcome.shipped()).isEqualTo(Crash.ORDERS);
    }
}
