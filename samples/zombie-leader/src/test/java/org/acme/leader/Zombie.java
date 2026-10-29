package org.acme.leader;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.FleetInstance;
import io.restassured.common.mapper.TypeRef;
import io.restassured.path.json.JsonPath;

/**
 * The steps of the scenario: freeze the leader while it is working on an item, let the other instance take the
 * lease and record that item, then let the old leader run again and see what it does with the item it was on.
 */
final class Zombie {

    private Zombie() {
    }

    record Outcome(int item, int zombie, List<Integer> duplicates) {
    }

    private record Working(FleetInstance leader, int item) {
    }

    static Outcome freezeTheLeaderMidWork(Fleet fleet) throws Exception {
        // whoever leads, caught in the middle of an item
        Working working = await("an instance takes the lease and starts an item").atMost(Duration.ofSeconds(10))
                .until(() -> workingLeader(fleet), Objects::nonNull);
        FleetInstance leader = working.leader();
        int item = working.item();
        leader.pause();

        // the lease expires, the other instance takes it, starts from the same item and records it
        FleetInstance successor = fleet.instance(1 - leader.index());
        await().atMost(Duration.ofSeconds(10)).until(() -> items(successor.url()).contains(item));

        // the old leader runs again: it finishes the item it was on
        leader.resume();
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            Map<String, Object> state = state(leader.url());
            return state.get("working") == null && Boolean.FALSE.equals(state.get("leader"));
        });

        return new Outcome(item, leader.index(), duplicates(successor.url()));
    }

    private static Working workingLeader(Fleet fleet) {
        for (FleetInstance candidate : fleet.instances()) {
            Map<String, Object> state = state(candidate.url());
            if (Boolean.TRUE.equals(state.get("leader")) && state.get("working") != null) {
                return new Working(candidate, (Integer) state.get("working"));
            }
        }
        return null;
    }

    private static Map<String, Object> state(URI instance) {
        return given().baseUri(instance.toString())
                .get("/leader")
                .as(new TypeRef<>() {
                });
    }

    private static List<Integer> items(URI instance) {
        return done(instance).getList("items", Integer.class);
    }

    private static List<Integer> duplicates(URI instance) {
        return done(instance).getList("duplicates", Integer.class);
    }

    private static JsonPath done(URI instance) {
        return given().baseUri(instance.toString())
                .get("/done")
                .jsonPath();
    }
}
