package org.acme.bookings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.restassured.response.Response;

/**
 * The bug: a booking counts the bookings for a slot, and if the count is below the capacity it inserts one. The
 * method is {@code synchronized}, which keeps two bookings on one instance apart, but a lock in the JVM is unknown
 * to the other instance. Two bookings on two instances both count zero, both pass the capacity check, and both
 * insert. The slot holds two bookings where one was allowed, and no error was raised. This is write skew: the rows
 * each transaction read were untouched, so nothing conflicted, yet together they broke the invariant.
 */
@QuarkusFleetTest(instances = 2)
class SlotOversoldIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void bothInstancesBookTheSameSlot() throws Exception {
        // the first booking counts zero and then holds, standing in for work before the insert
        CompletableFuture<Response> alice = CompletableFuture
                .supplyAsync(() -> Bookings.book(fleet, 0, "A", "alice", 1500));
        // let the first booking open its transaction and count zero before the second one starts
        Thread.sleep(300);

        // the other instance counts the same zero, sees room, and books while the first is still holding
        assertThat(Bookings.book(fleet, 1, "A", "bob", 0).statusCode()).isEqualTo(200);

        // the first instance believed the slot was empty too, so it also books
        assertThat(alice.join().statusCode()).isEqualTo(200);

        // two bookings for a slot whose capacity is one
        assertThat(Bookings.count(fleet, 0, "A")).isEqualTo(2);
    }
}
