package io.quarkiverse.fleettest.it;

import static io.restassured.RestAssured.given;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.FleetInstance;

// The instance endpoint as the tests use it: which instance answers, and flipping an instance's readiness.
final class Instances {

    private Instances() {
    }

    // RestAssured opens a connection per request, so each call is balanced independently.
    static String serving() {
        return given().get("/instance").then().statusCode(200).extract().body().asString();
    }

    static void setReady(FleetInstance instance, boolean ready) {
        given().baseUri(instance.url().toString()).post("/instance/ready/" + ready).then().statusCode(204);
    }

    static void setAllReady(Fleet fleet, boolean ready) {
        for (FleetInstance instance : fleet.instances()) {
            setReady(instance, ready);
        }
    }
}
