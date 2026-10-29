package org.acme.notifications;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;

/**
 * A plain {@code @QuarkusTest}: one instance, with the double-send bug switched on (the default). Two drains race
 * against the same process, the way a developer would try to provoke the bug without a fleet. The poller is
 * synchronized, because in production it is a single {@code @Scheduled} method that never runs twice at once in
 * one JVM, so the second drain only starts once the first has marked its rows sent and finds nothing left to do.
 * Each notification goes out once. The double-send cannot be reproduced on one instance, which is exactly why it
 * reaches production unseen and why the fleet test earns its place.
 */
@QuarkusTest
class OneInstanceSendsOnceTest {

    @Test
    void twoDrainsOnOneInstanceDoNotDoubleSend() throws Exception {
        given().post("/notifications/10").then().statusCode(204);

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> Outbox.drain(2000));
        await().atMost(Duration.ofSeconds(10)).until(() -> Outbox.holding() == 1);
        int second = Outbox.drain(0);

        JsonPath summary = given().get("/outbox/summary").jsonPath();

        assertThat(first.join()).isEqualTo(10);
        assertThat(second).isZero();
        assertThat(summary.getLong("sent")).isEqualTo(10);
        assertThat(summary.getLong("pending")).isZero();
        assertThat(summary.getList("duplicates", Integer.class)).isEmpty();
    }
}
