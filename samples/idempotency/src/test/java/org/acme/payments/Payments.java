package org.acme.payments;

import static io.restassured.RestAssured.given;

import java.util.Map;
import java.util.UUID;

import io.restassured.response.Response;

/**
 * A client that retries a payment with an {@code Idempotency-Key}, the way a client is supposed to.
 */
final class Payments {

    private Payments() {
    }

    static String newKey() {
        return UUID.randomUUID().toString();
    }

    static Response pay(String key, int amount) {
        return paySlowly(key, amount, 0);
    }

    // A payment that takes a while to process, for a retry that arrives while the first attempt is still running.
    static Response paySlowly(String key, int amount, long millis) {
        return given().header("Idempotency-Key", key)
                .contentType("application/json")
                .body(Map.of("account", "acme", "amount", amount))
                .post("/payments?delayMillis=" + millis);
    }

    static long count() {
        return given().get("/payments").jsonPath().getLong("payments");
    }
}
