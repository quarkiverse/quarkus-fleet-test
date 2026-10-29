package org.acme.account;

import static io.restassured.RestAssured.given;

import io.quarkiverse.fleettest.Fleet;

final class Login {

    private Login() {
    }

    // Logs in through the instance the balancer is pinned to and returns the credential cookie it issued.
    static String through(Fleet fleet, int instance) {
        fleet.pin(instance);
        return given()
                .redirects()
                .follow(false)
                .formParam("j_username", "alice")
                .formParam("j_password", "secret")
                .post("/j_security_check")
                .then()
                .statusCode(302)
                .extract()
                .cookie("quarkus-credential");
    }

    // The status of a request to the account page with that cookie, through another instance.
    static int accountStatusThrough(Fleet fleet, int instance, String cookie) {
        fleet.pin(instance);
        return given()
                .redirects()
                .follow(false)
                .cookie("quarkus-credential", cookie)
                .get("/account")
                .statusCode();
    }
}
