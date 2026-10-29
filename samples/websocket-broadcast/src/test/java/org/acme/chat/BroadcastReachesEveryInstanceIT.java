package org.acme.chat;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The fix: {@code chat.relay=redis}. The message is published on a Redis channel every instance subscribes to,
 * and each instance delivers it to its own connections, so the clients on both instances get it.
 */
@QuarkusFleetTest(instances = 2)
@TestProfile(RedisRelayProfile.class)
class BroadcastReachesEveryInstanceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theClientsOnEveryInstanceGetTheMessage() throws Exception {
        try (ChatClient a = ChatClient.connectThrough(fleet, 0); ChatClient b = ChatClient.connectThrough(fleet, 1)) {
            assertThat(a.instance()).isEqualTo(0);
            assertThat(b.instance()).isEqualTo(1);

            fleet.pin(0);
            given().contentType("text/plain")
                    .body("hello")
                    .post("/messages")
                    .then()
                    .statusCode(204);

            assertThat(a.nextMessage(Duration.ofSeconds(5))).isEqualTo("hello");
            assertThat(b.nextMessage(Duration.ofSeconds(5))).isEqualTo("hello");
        }
    }
}
