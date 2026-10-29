package org.acme.payments;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;

/**
 * The fix: {@code quarkus.idempotency.store=jdbc}, the store in the database every instance shares. A retry is
 * replayed whichever instance it lands on, a retry while the first attempt is still running on another instance
 * is refused, and a different payload under the same key is rejected, all across instances.
 */
@QuarkusFleetTest(instances = 2)
@TestProfile(JdbcStoreProfile.class)
class PaymentChargedOnceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void aRetryOnTheOtherInstanceIsReplayed() {
        String key = Payments.newKey();
        fleet.pin(0);
        Response first = Payments.pay(key, 100);
        assertThat(first.statusCode()).isEqualTo(200);
        long before = Payments.count();

        fleet.pin(1);
        Response retry = Payments.pay(key, 100);
        assertThat(retry.statusCode()).isEqualTo(200);
        assertThat(retry.header("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.body().asString()).isEqualTo(first.body().asString());
        assertThat(Payments.count()).isEqualTo(before);
    }

    @Test
    void aRetryWhileTheFirstAttemptRunsOnTheOtherInstanceIsRefused() throws Exception {
        String key = Payments.newKey();
        fleet.pin(0);
        CompletableFuture<Response> first = CompletableFuture.supplyAsync(() -> Payments.paySlowly(key, 100, 1500));
        Thread.sleep(300);

        // the same request again, byte for byte: a different one under the same key would be a mismatch, 422
        fleet.pin(1);
        Response concurrent = Payments.paySlowly(key, 100, 1500);
        assertThat(concurrent.statusCode()).isEqualTo(409);

        assertThat(first.get().statusCode()).isEqualTo(200);
    }

    @Test
    void aDifferentPayloadUnderTheSameKeyOnTheOtherInstanceIsRejected() {
        String key = Payments.newKey();
        fleet.pin(0);
        assertThat(Payments.pay(key, 100).statusCode()).isEqualTo(200);

        fleet.pin(1);
        assertThat(Payments.pay(key, 250).statusCode()).isEqualTo(422);
    }
}
