package org.acme.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;

/**
 * The fix: both transfers acquire the two row locks in the same order, lower id first, whichever way the money
 * moves. One of them now simply waits for the other on the first lock instead of forming a cycle, so both commit.
 * The cure is a consistent global lock order, not more locking.
 */
@QuarkusFleetTest(instances = 2)
@TestProfile(LockOrderByIdProfile.class)
class LockOrderingAvoidsDeadlockIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void bothTransfersCommit() throws Exception {
        // the same two opposite transfers that deadlocked, each on its own instance
        CompletableFuture<Response> aToB = CompletableFuture.supplyAsync(() -> Transfers.transfer(fleet, 0, 1, 2, 10, 1500));
        CompletableFuture<Response> bToA = CompletableFuture.supplyAsync(() -> Transfers.transfer(fleet, 1, 2, 1, 10, 1500));

        assertThat(aToB.get(30, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        assertThat(bToA.get(30, TimeUnit.SECONDS).statusCode()).isEqualTo(200);

        // 10 each way nets to zero, so both accounts are back at their opening balance
        assertThat(Transfers.balance(fleet, 0, 1)).isEqualTo(100);
        assertThat(Transfers.balance(fleet, 1, 2)).isEqualTo(100);
    }
}
