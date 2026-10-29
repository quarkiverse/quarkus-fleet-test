package org.acme.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The fix: {@code SELECT ... FOR UPDATE}. The row lock lives in the database every instance shares, so the second
 * withdrawal waits until the first has committed, then reads 70 and writes 20.
 */
@QuarkusFleetTest(instances = 2)
@TestProfile(LockRowsProfile.class)
class WithdrawalsSerialisedAcrossInstancesIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void aWithdrawalOnTheOtherInstanceWaitsForTheFirst() throws Exception {
        // both instances see the opening balance, and are warm
        fleet.pin(0);
        assertThat(Wallets.balance(1)).isEqualTo(100);
        fleet.pin(1);
        assertThat(Wallets.balance(1)).isEqualTo(100);

        fleet.pin(0);
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> Wallets.withdraw(1, 30, 1500));
        // the first withdrawal has locked the row and is waiting on the fraud check
        Thread.sleep(300);

        // the other instance blocks on the row lock until the first withdrawal commits
        fleet.pin(1);
        assertThat(Wallets.withdraw(1, 50, 0)).isEqualTo(20);
        assertThat(first.join()).isEqualTo(70);
        assertThat(Wallets.balance(1)).isEqualTo(20);
    }
}
