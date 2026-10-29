package org.acme.leader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The fix: a fencing token. Every write carries the token the lease was acquired with, and the database accepts
 * it only while the lease still carries that token. The old leader's write is rejected, and the item is done
 * once.
 */
@QuarkusFleetTest(instances = 2)
@TestProfile(FencedProfile.class)
class FencingTokenRejectsZombieIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theFrozenLeadersWriteIsRejected() throws Exception {
        Zombie.Outcome outcome = Zombie.freezeTheLeaderMidWork(fleet);
        assertThat(outcome.duplicates()).isEmpty();
    }
}
