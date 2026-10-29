package org.acme.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.restassured.response.Response;

/**
 * The bug: two transfers lock the same two rows in opposite order. One instance never sees it, because the
 * {@code synchronized} transfer method serialises transfers within that JVM. Across two instances the lock does
 * nothing: A to B locks row 1 then waits for row 2, B to A locks row 2 then waits for row 1, and the two wait on
 * each other forever until Postgres detects the cycle and aborts one of them.
 */
@QuarkusFleetTest(instances = 2)
class OppositeTransfersDeadlockIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void oneTransferIsAbortedToBreakTheCycle() throws Exception {
        // the 1500ms hold makes both transfers take their first lock before either reaches for its second, so the
        // cycle is guaranteed to form; each call goes to its own instance so the two transactions run in parallel
        CompletableFuture<Response> aToB = CompletableFuture.supplyAsync(() -> Transfers.transfer(fleet, 0, 1, 2, 10, 1500));
        CompletableFuture<Response> bToA = CompletableFuture.supplyAsync(() -> Transfers.transfer(fleet, 1, 2, 1, 10, 1500));

        // Postgres deadlock_timeout defaults to one second, so one side is aborted well inside this timeout
        Response first = aToB.get(30, TimeUnit.SECONDS);
        Response second = bToA.get(30, TimeUnit.SECONDS);

        // exactly one transfer is aborted with 409 deadlock, the other commits with 200
        assertThat(List.of(first.statusCode(), second.statusCode())).containsExactlyInAnyOrder(200, 409);
        Response aborted = first.statusCode() == 409 ? first : second;
        assertThat(aborted.asString()).isEqualTo("deadlock");
    }
}
