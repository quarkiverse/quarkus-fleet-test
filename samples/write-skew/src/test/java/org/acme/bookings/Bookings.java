package org.acme.bookings;

import static io.restassured.RestAssured.given;

import io.quarkiverse.fleettest.Fleet;
import io.restassured.response.Response;

/**
 * The booking API as a client sees it. Every call goes straight to one instance, bypassing the load balancer, so
 * that the two bookings genuinely run on the two instances rather than being balanced to whichever is free.
 */
final class Bookings {

    private Bookings() {
    }

    // Books the slot on the given instance. The response is 200 if the booking was made, 409 if the slot was full.
    static Response book(Fleet fleet, int instance, String slot, String who, long holdMillis) {
        return given().baseUri(fleet.instance(instance).url().toString())
                .queryParam("slot", slot)
                .queryParam("who", who)
                .queryParam("holdMillis", holdMillis)
                .post("/bookings");
    }

    static int count(Fleet fleet, int instance, String slot) {
        return Integer.parseInt(given().baseUri(fleet.instance(instance).url().toString())
                .get("/bookings/" + slot)
                .then()
                .statusCode(200)
                .extract()
                .asString());
    }
}
