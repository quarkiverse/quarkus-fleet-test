package org.acme.leader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

/**
 * The bug: a leader frozen for longer than its lease (a long GC pause, a VM migration, a network partition) does
 * not know it lost the lease. When it runs again it records the item it was on, which its successor already
 * processed: the item is done twice.
 */
@QuarkusFleetTest(instances = 2)
class ZombieLeaderRecordsTwiceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theItemTheFrozenLeaderWasOnIsRecordedTwice() throws Exception {
        Zombie.Outcome outcome = Zombie.freezeTheLeaderMidWork(fleet);
        assertThat(outcome.duplicates()).contains(outcome.item());
    }
}
