package org.acme.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

/**
 * The bug: a withdrawal reads the balance, waits for a fraud check, and writes the balance minus the amount. The
 * method is {@code synchronized}, which keeps two withdrawals on one instance apart, but a lock in the JVM is
 * unknown to the other instance. Two withdrawals on two instances both read 100; the second writes 50 and the
 * first writes 70 over it. Fifty units vanished.
 */
@QuarkusFleetTest(instances = 2)
class WithdrawalLostOnTwoInstancesIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void aWithdrawalOnTheOtherInstanceIsOverwritten() throws Exception {
        // both instances see the opening balance, and are warm
        fleet.pin(0);
        assertThat(Wallets.balance(1)).isEqualTo(100);
        fleet.pin(1);
        assertThat(Wallets.balance(1)).isEqualTo(100);

        fleet.pin(0);
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> Wallets.withdraw(1, 30, 1500));
        // the first withdrawal has read the balance and is waiting on the fraud check
        Thread.sleep(300);

        // the other instance reads the same 100 and writes 50
        fleet.pin(1);
        assertThat(Wallets.withdraw(1, 50, 0)).isEqualTo(50);

        // the first instance still believes the balance was 100, and writes 70 over the 50
        assertThat(first.join()).isEqualTo(70);
        assertThat(Wallets.balance(1)).isEqualTo(70);
    }

    @Test
    void twoWithdrawalsOnOneInstanceAreSerialised() throws Exception {
        fleet.pin(0);
        assertThat(Wallets.balance(2)).isEqualTo(100);
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> Wallets.withdraw(2, 30, 1500));
        // the first withdrawal has read the balance and is waiting on the fraud check
        Thread.sleep(300);

        // the in-JVM lock makes the second withdrawal wait for the first, so it reads 70 and writes 20: this is why a
        // single-instance test never finds the bug
        assertThat(Wallets.withdraw(2, 50, 0)).isEqualTo(20);
        assertThat(first.join()).isEqualTo(70);
        assertThat(Wallets.balance(2)).isEqualTo(20);
    }
}
