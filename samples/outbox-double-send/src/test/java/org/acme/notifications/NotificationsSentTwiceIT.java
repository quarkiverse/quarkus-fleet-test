package org.acme.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

/**
 * The bug: the poller selects the pending rows and marks them sent only after the mail provider has taken them.
 * In between, the poller on the other instance selects the same rows, because nothing says they are taken. Every
 * notification goes out twice. On one instance the poller never overlaps with itself, so a {@code @QuarkusTest}
 * never sees this.
 */
@QuarkusFleetTest(instances = 2)
class NotificationsSentTwiceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void bothInstancesSendTheSameNotifications() throws Exception {
        Outbox.Outcome outcome = Outbox.drainFromBothInstancesAtOnce(fleet);
        assertThat(outcome.secondDrainSent()).isEqualTo(10);
        assertThat(outcome.firstDrainSent()).isEqualTo(10);
        assertThat(outcome.pending()).isZero();
        assertThat(outcome.sent()).isEqualTo(20);
        assertThat(outcome.duplicates()).hasSize(10);
    }
}
