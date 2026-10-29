package org.acme.chat;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

/**
 * The bug: a broadcast goes to the WebSocket connections of the process that sends it. The balancer pins each
 * connection to the instance that accepted it, so the clients connected to the other instance never hear the
 * message. With one instance every client is on it and the broadcast looks complete.
 */
@QuarkusFleetTest(instances = 2)
class BroadcastReachesOneInstanceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theClientsOnTheOtherInstanceDoNotGetTheMessage() throws Exception {
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
            // instance 1 never heard of the message, so B has nothing to receive
            assertThat(b.nextMessage(Duration.ofSeconds(2))).isNull();
        }
    }
}
