package org.acme.notifications;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.FleetInstance;
import io.restassured.path.json.JsonPath;

/**
 * The steps of the scenario: fill the outbox, start a drain on one instance that holds its rows for a while, and
 * run a second drain on the other instance before the first one is done, the way two schedulers overlap.
 */
final class Outbox {

    private Outbox() {
    }

    record Outcome(int firstDrainSent, int secondDrainSent, long pending, long sent, List<Integer> duplicates) {
    }

    static Outcome drainFromBothInstancesAtOnce(Fleet fleet) throws Exception {
        given().post("/notifications/10")
                .then()
                .statusCode(204);

        // the first instance selects its rows and then spends two seconds at the mail provider
        fleet.pin(0);
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> drain(2000));
        await().atMost(Duration.ofSeconds(10)).until(() -> holding(fleet.instance(0)) == 1);

        // the other instance's scheduler fires while the first is still at it
        fleet.pin(1);
        int second = drain(0);
        int firstSent = first.join();

        JsonPath summary = given().get("/outbox/summary").jsonPath();
        return new Outcome(firstSent, second, summary.getLong("pending"), summary.getLong("sent"),
                summary.getList("duplicates", Integer.class));
    }

    // How many drains of that instance have selected their rows and are waiting on the mail provider.
    static int holding(FleetInstance instance) {
        return Integer.parseInt(given().baseUri(instance.url().toString())
                .get("/outbox/holding")
                .asString());
    }

    // The same, through the load balancer: for a test with one instance behind it.
    static int holding() {
        return Integer.parseInt(given().get("/outbox/holding").asString());
    }

    static int drain(long holdMillis) {
        return Integer.parseInt(given().post("/outbox/drain?holdMillis=" + holdMillis)
                .then()
                .statusCode(200)
                .extract()
                .asString());
    }
}
