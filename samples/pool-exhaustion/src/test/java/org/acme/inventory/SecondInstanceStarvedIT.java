package org.acme.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

/**
 * The bug: a connection pool sized as if the instance were alone. Two pools of 8 want 16 connections from a
 * database that accepts 12. The first instance to start takes its 8; the second gets the 4 that are left, logs the
 * refusals, and reports ready like any other, so the balancer sends it its full share of the traffic, half of which
 * fails for want of a connection. On one instance the pool is always full, which is why a {@code @QuarkusTest}
 * never sees this.
 */
@QuarkusFleetTest(instances = 2)
class SecondInstanceStarvedIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theSecondInstanceIsReadyAndCannotServe() {
        // nothing flagged it: readiness is about the process, not about what the database let it have
        assertThat(fleet.instance(1).isReady()).isTrue();

        // well within what its pool promises, yet only the connections it actually got are served
        List<Integer> second = Burst.stockCounts(fleet, 1, 6);
        assertThat(second).contains(200);
        assertThat(second).contains(500);

        // the first instance holds the whole pool it asked for
        assertThat(Burst.stockCounts(fleet, 0, 6)).containsOnly(200);
    }
}
