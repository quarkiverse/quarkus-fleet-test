package org.acme.quotes;

import static io.restassured.RestAssured.given;

import io.restassured.response.Response;

// One client, alice, asking for a quote. The limit is per client, so every request carries her name.
final class Quotes {

    private Quotes() {
    }

    static Response quote() {
        return given().header("X-Client", "alice")
                .get("/quotes");
    }
}
