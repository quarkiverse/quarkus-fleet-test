package org.acme.payments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.restassured.response.Response;

/**
 * The bug: the default {@code in-memory} idempotency store. The key is known to the instance that served the
 * first attempt and to no other, so a retry that lands on the next instance is a new payment. On one instance
 * the extension works exactly as documented, which is why a {@code @QuarkusTest} never sees this.
 */
@QuarkusFleetTest(instances = 2)
class PaymentChargedTwiceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void aRetryOnTheOtherInstanceIsANewPayment() {
        String key = Payments.newKey();
        fleet.pin(0);
        Response first = Payments.pay(key, 100);
        assertThat(first.statusCode()).isEqualTo(200);

        // the same instance replays the stored response
        Response sameInstance = Payments.pay(key, 100);
        assertThat(sameInstance.statusCode()).isEqualTo(200);
        assertThat(sameInstance.header("Idempotent-Replayed")).isEqualTo("true");
        assertThat(Payments.count()).isEqualTo(1);

        // the other instance has never heard of the key
        fleet.pin(1);
        Response retry = Payments.pay(key, 100);
        assertThat(retry.statusCode()).isEqualTo(200);
        assertThat(retry.header("Idempotent-Replayed")).isNull();
        assertThat(Payments.count()).isEqualTo(2);
    }
}
