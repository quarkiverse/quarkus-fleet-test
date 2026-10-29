package org.acme.subscriptions;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * A plain {@code @QuarkusTest}: one instance, with the out-of-order bug switched on (the default). Two applies of
 * the same subscription race against the same process, the way a developer would try to provoke the bug without
 * a fleet. Apply is synchronized, because in production it is a single {@code @Scheduled} poller that applies one
 * event at a time in one JVM, so the later event (cancel) only runs once the earlier one (activate) has
 * committed, and the subscription ends in event order. The anomaly cannot be reproduced on one instance, which
 * is why it reaches production unseen and why the fleet test earns its place.
 */
@QuarkusTest
class OneInstanceAppliesInOrderTest {

    @Test
    void twoAppliesOnOneInstanceStayInOrder() throws Exception {
        int activate = Events.enqueue(7, 1, "activate");
        int cancel = Events.enqueue(7, 2, "cancel");

        CompletableFuture<Integer> first = CompletableFuture
                .supplyAsync(() -> apply(activate, 1500));
        Thread.sleep(500);
        assertThat(apply(cancel, 0)).isEqualTo(200);
        assertThat(first.join()).isEqualTo(200);

        SubscriptionsService.Subscription subscription = given().get("/subscriptions/7")
                .then().statusCode(200).extract().as(SubscriptionsService.Subscription.class);
        assertThat(subscription.state()).isEqualTo("cancelled");
        assertThat(subscription.lastSeq()).isEqualTo(2);
    }

    private static int apply(int eventId, long holdMillis) {
        return given().post("/events/apply?eventId=" + eventId + "&holdMillis=" + holdMillis).statusCode();
    }
}
