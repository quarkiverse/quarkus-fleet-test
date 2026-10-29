package org.acme.orders;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

/**
 * The bug: the consumer crashes after shipping the orders and before committing its offsets. The instance that
 * takes the partition over receives the same orders and ships them again.
 */
@QuarkusFleetTest(instances = 2, running = 1)
class OrdersShippedTwiceAfterCrashIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void everyOrderReceivedAgainIsShippedAgain() throws Exception {
        Crash.Outcome outcome = Crash.ofTheConsumerBeforeItCommits(fleet);
        assertThat(outcome.received()).isGreaterThan(Crash.ORDERS);
        assertThat(outcome.shipped()).isEqualTo(outcome.received());
    }
}
