package org.acme.wallet;

import static io.restassured.RestAssured.given;

/**
 * The wallet API as a client sees it: the balance, and a withdrawal that may be held for a while between reading
 * the balance and writing it, long enough for another withdrawal to come in.
 */
final class Wallets {

    private Wallets() {
    }

    static int balance(int wallet) {
        return Integer.parseInt(given().get("/wallets/" + wallet)
                .then()
                .statusCode(200)
                .extract()
                .asString());
    }

    // Returns the balance after the withdrawal, as the instance that made it reports it.
    static int withdraw(int wallet, int amount, long holdMillis) {
        return Integer.parseInt(given().queryParam("amount", amount)
                .queryParam("holdMillis", holdMillis)
                .post("/wallets/" + wallet + "/withdraw")
                .then()
                .statusCode(200)
                .extract()
                .asString());
    }
}
