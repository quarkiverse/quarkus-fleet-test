package org.acme.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The fix: a pool sized per replica, the database's 12 connections divided by the 2 instances. Every instance gets
 * the 6 it asks for, so a burst of 6 is served whichever instance it lands on.
 */
@QuarkusFleetTest(instances = 2)
@TestProfile(SizedPerReplicaProfile.class)
class EveryInstanceHasConnectionsIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void bothInstancesServeAFullBurst() {
        assertThat(Burst.stockCounts(fleet, 1, 6)).containsOnly(200);
        assertThat(Burst.stockCounts(fleet, 0, 6)).containsOnly(200);
    }
}
