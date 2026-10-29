package org.acme.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The fix: {@code SELECT ... FOR UPDATE SKIP LOCKED}. The rows a poller selects stay locked until it commits, and
 * a poller on another instance skips them instead of waiting for them or sending them again. The second drain
 * finds nothing free to do.
 */
@QuarkusFleetTest(instances = 2)
@TestProfile(SkipLockedProfile.class)
class NotificationsSentOnceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theOtherInstanceSkipsTheRowsTheFirstOneHolds() throws Exception {
        Outbox.Outcome outcome = Outbox.drainFromBothInstancesAtOnce(fleet);
        assertThat(outcome.secondDrainSent()).isZero();
        assertThat(outcome.firstDrainSent()).isEqualTo(10);
        assertThat(outcome.pending()).isZero();
        assertThat(outcome.sent()).isEqualTo(10);
        assertThat(outcome.duplicates()).isEmpty();
    }
}
