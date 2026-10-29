package org.acme.subscriptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

/**
 * The bug: two instances pull causally ordered events for the same subscription from the shared table and apply
 * them with nothing to serialise them. Instance 0 is slow applying the earlier event (activate), instance 1
 * applies the later event (cancel) in the meantime, and then instance 0 finishes and blindly writes activate
 * last. The subscription ends up "active" though the later event was cancel. On one instance the poller never
 * overlaps with itself, so a {@code @QuarkusTest} never sees this.
 */
@QuarkusFleetTest(instances = 2)
class OutOfOrderLeavesWrongStateIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theLaterEventIsOverwrittenByTheEarlierOne() throws Exception {
        int activate = Events.enqueue(7, 1, "activate");
        int cancel = Events.enqueue(7, 2, "cancel");

        // instance 0 starts applying the earlier event and spends a long time at it
        CompletableFuture<Integer> first = CompletableFuture
                .supplyAsync(() -> Events.apply(fleet, 0, activate, 1500).statusCode());

        // let instance 0's apply read the subscription and get into its hold before instance 1 starts
        Thread.sleep(500);

        // instance 1 applies the later event quickly and commits "cancelled"
        assertThat(Events.apply(fleet, 1, cancel, 0).statusCode()).isEqualTo(200);

        // instance 0 wakes and commits "active" last, overwriting the cancel
        assertThat(first.join()).isEqualTo(200);

        SubscriptionsService.Subscription subscription = Events.subscription(fleet, 0, 7);
        assertThat(subscription.state()).isEqualTo("active");
    }
}
