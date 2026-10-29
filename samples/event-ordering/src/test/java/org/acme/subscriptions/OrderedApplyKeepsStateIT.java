package org.acme.subscriptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The fix: each apply locks the aggregate row ({@code SELECT ... FOR UPDATE}) for its whole run and applies only
 * events newer than the last one applied. Instance 0 takes the lock first for the earlier event, so instance 1's
 * apply of the later event blocks until instance 0 commits. Instance 0 applies activate (last_seq 1), then
 * instance 1 applies cancel (seq 2 > 1), so the subscription ends "cancelled" at seq 2, in event order.
 */
@QuarkusFleetTest(instances = 2)
@TestProfile(OrderedApplyProfile.class)
class OrderedApplyKeepsStateIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void sameKeyEventsSerialiseInOrder() throws Exception {
        int activate = Events.enqueue(7, 1, "activate");
        int cancel = Events.enqueue(7, 2, "cancel");

        // instance 0 starts first and takes the subscription row lock before holding
        CompletableFuture<Integer> first = CompletableFuture
                .supplyAsync(() -> Events.apply(fleet, 0, activate, 1500).statusCode());

        // let instance 0's apply take the lock before instance 1 tries to
        Thread.sleep(500);

        // instance 1's apply blocks on the lock until instance 0 commits, then applies the later event
        assertThat(Events.apply(fleet, 1, cancel, 0).statusCode()).isEqualTo(200);
        assertThat(first.join()).isEqualTo(200);

        SubscriptionsService.Subscription subscription = Events.subscription(fleet, 0, 7);
        assertThat(subscription.state()).isEqualTo("cancelled");
        assertThat(subscription.lastSeq()).isEqualTo(2);
    }
}
