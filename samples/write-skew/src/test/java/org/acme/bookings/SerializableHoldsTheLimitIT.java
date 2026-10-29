package org.acme.bookings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;

/**
 * The fix: SERIALIZABLE isolation, set only by this profile's runtime config. No row lock can help, because the
 * rows that would break the count invariant do not exist when the bookings read it, and no unique constraint can
 * express a count limit. Under serializable, Postgres tracks the reads and refuses to let both commit: it raises
 * SQLSTATE 40001 on the losing transaction, which the booking method retries, re-counts, and finds the slot full.
 */
@QuarkusFleetTest(instances = 2)
@TestProfile(SerializableProfile.class)
class SerializableHoldsTheLimitIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void onlyOneInstanceBooksTheSlot() throws Exception {
        // the first booking counts zero and then holds, standing in for work before the insert
        CompletableFuture<Response> alice = CompletableFuture
                .supplyAsync(() -> Bookings.book(fleet, 0, "A", "alice", 1500));
        // let the first booking open its transaction and count zero before the second one starts
        Thread.sleep(300);

        // the second booking has no hold, so it counts zero and commits first: it wins
        assertThat(Bookings.book(fleet, 1, "A", "bob", 0).statusCode()).isEqualTo(200);

        // the first booking resumes and tries to commit over the committed write. It is the later committer, so
        // Postgres aborts it with 40001; the retry counts one, sees the slot full, and returns 409
        assertThat(alice.join().statusCode()).isEqualTo(409);

        // the invariant held: one booking for the slot
        assertThat(Bookings.count(fleet, 0, "A")).isEqualTo(1);
    }
}
