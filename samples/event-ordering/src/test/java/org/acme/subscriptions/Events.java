package org.acme.subscriptions;

import static io.restassured.RestAssured.given;

import io.quarkiverse.fleettest.Fleet;
import io.restassured.response.Response;

/**
 * The steps of the scenario, driven one event at a time: enqueue events for a subscription, then apply a given
 * event on a given instance. Apply goes straight to the instance, bypassing the balancer, so the test pins each
 * apply to the instance it wants and controls exactly how the two interleave.
 */
final class Events {

    private Events() {
    }

    // Enqueue one event and return its id, so the test can later apply it by id on a chosen instance.
    static int enqueue(int subscriptionId, int seq, String type) {
        return Integer.parseInt(given().post("/events/" + subscriptionId + "/" + seq + "/" + type)
                .then()
                .statusCode(200)
                .extract()
                .asString());
    }

    // Apply one event on one instance. Posted directly to that instance's URL, not through the balancer, so the
    // test decides which process applies which event.
    static Response apply(Fleet fleet, int instance, int eventId, long holdMillis) {
        return given().baseUri(fleet.instance(instance).url().toString())
                .post("/events/apply?eventId=" + eventId + "&holdMillis=" + holdMillis);
    }

    static SubscriptionsService.Subscription subscription(Fleet fleet, int instance, int id) {
        return given().baseUri(fleet.instance(instance).url().toString())
                .get("/subscriptions/" + id)
                .then()
                .statusCode(200)
                .extract()
                .as(SubscriptionsService.Subscription.class);
    }
}
