package org.acme.pricing;

import static io.restassured.RestAssured.given;

import java.util.Map;

import io.quarkiverse.fleettest.Fleet;

/**
 * The price of A1 as seen through a given instance, and a price change made through a given instance.
 */
final class Prices {

    private Prices() {
    }

    static int priceThrough(Fleet fleet, int instance) {
        fleet.pin(instance);
        return Integer.parseInt(given().get("/prices/A1")
                .then()
                .statusCode(200)
                .extract()
                .asString());
    }

    static void changeThrough(Fleet fleet, int instance, int amount) {
        fleet.pin(instance);
        given().contentType("application/json")
                .body(Map.of("amount", amount))
                .put("/prices/A1")
                .then()
                .statusCode(204);
    }
}
