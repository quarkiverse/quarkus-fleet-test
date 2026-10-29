package org.acme.orders;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.FleetInstance;
import io.restassured.path.json.JsonPath;

/**
 * The steps of the scenario: place orders on the one running instance, let it ship them all, kill it before it
 * commits its offsets, then start a second instance and wait for it to take the partition and receive the same
 * orders again.
 */
final class Crash {

    private Crash() {
    }

    static final int ORDERS = 20;

    record Outcome(long received, long shipped) {
    }

    static Outcome ofTheConsumerBeforeItCommits(Fleet fleet) throws Exception {
        // Only instance 0 is running, so it is the single member of the consumer group and owns the one
        // partition from the start, with no startup rebalance to flake on.
        FleetInstance consumer = fleet.instance(0);
        given().post("/orders/" + ORDERS)
                .then()
                .statusCode(204);
        await().atMost(Duration.ofSeconds(30)).until(() -> summary(consumer.url()).getLong("received") == ORDERS);

        // It crashes after shipping the orders and before committing its offsets, so the group has no record of
        // how far it got. A graceful stop would commit on the way out; a kill cannot, which is the whole point.
        consumer.kill();

        // A second instance starts, joins the group, and reads from the last committed offset, which is the
        // start: it receives every order again, because an uncommitted crash is what at-least-once redelivery is.
        FleetInstance survivor = fleet.instance(1);
        survivor.start();
        await().atMost(Duration.ofSeconds(60)).ignoreExceptions()
                .until(() -> receivedBy(survivor) >= ORDERS);

        JsonPath summary = summary(survivor.url());
        return new Outcome(summary.getLong("received"), summary.getLong("shipped"));
    }

    // The orders the given instance received, as the summary breaks them down by instance.
    private static long receivedBy(FleetInstance instance) {
        Map<String, Long> byInstance = summary(instance.url()).getMap("receivedBy", String.class, Long.class);
        return byInstance.getOrDefault(Integer.toString(instance.index()), 0L);
    }

    private static JsonPath summary(URI base) {
        return given().baseUri(base.toString())
                .get("/orders")
                .jsonPath();
    }
}
