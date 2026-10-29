package org.acme.imports;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;

import io.quarkiverse.fleettest.Fleet;
import io.restassured.path.json.JsonPath;

/**
 * The steps of the scenario: start an import on one instance, let it get well under way, and kill that instance.
 * What happens to the import afterwards is what the two tests disagree on.
 */
final class Imports {

    private Imports() {
    }

    static final int ROWS = 600;

    // Starts an import on instance 0, waits until a good part of it is done, kills instance 0, and returns the
    // import's id. Reads go through the load balancer afterwards, which only has the survivor left.
    static int startOnAnInstanceAndKillIt(Fleet fleet) {
        fleet.pin(0);
        int id = Integer.parseInt(given().post("/imports?rows=" + ROWS)
                .then()
                .statusCode(200)
                .extract()
                .asString());
        await().atMost(Duration.ofSeconds(30)).until(() -> rows(id) >= 100);

        fleet.instance(0).kill();
        fleet.roundRobin();
        return id;
    }

    static long rows(int id) {
        return status(id).getLong("rows");
    }

    static int processed(int id) {
        return status(id).getInt("processed");
    }

    static List<Integer> instances(int id) {
        return status(id).getList("instances", Integer.class);
    }

    private static JsonPath status(int id) {
        return given().get("/imports/" + id).jsonPath();
    }
}
