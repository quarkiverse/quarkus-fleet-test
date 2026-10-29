package org.acme.accounts;

import static io.restassured.RestAssured.given;

import io.quarkiverse.fleettest.Fleet;
import io.restassured.response.Response;

/**
 * The transfer API as a client sees it. Each call goes straight to one instance's own URL, bypassing the load
 * balancer, so the two concurrent transfers truly run on the two instances and the deadlock is between their
 * database transactions, not an artefact of routing.
 */
final class Transfers {

    private Transfers() {
    }

    // Moves amount from one account to the other on the given instance, holding the first row lock for holdMillis
    // before asking for the second. Returns the raw response so the caller can read the status and body.
    static Response transfer(Fleet fleet, int instance, int from, int to, int amount, long holdMillis) {
        return given().baseUri(fleet.instance(instance).url().toString())
                .queryParam("from", from)
                .queryParam("to", to)
                .queryParam("amount", amount)
                .queryParam("holdMillis", holdMillis)
                .post("/accounts/transfer");
    }

    static int balance(Fleet fleet, int instance, int id) {
        return Integer.parseInt(given().baseUri(fleet.instance(instance).url().toString())
                .get("/accounts/" + id)
                .then()
                .statusCode(200)
                .extract()
                .asString());
    }
}
