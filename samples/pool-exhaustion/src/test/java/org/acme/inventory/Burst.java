package org.acme.inventory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.RawHttp;

final class Burst {

    // One thread per request, so that every request of a burst is in flight at the same time.
    static final ExecutorService CLIENTS = Executors.newVirtualThreadPerTaskExecutor();

    private Burst() {
    }

    // Stock counts asked of the given instance at the same time; their status codes, -1 for a connection error.
    // Each one holds a database connection for two seconds, so a burst needs as many connections as requests.
    static List<Integer> stockCounts(Fleet fleet, int instance, int count) {
        fleet.pin(instance);
        List<CompletableFuture<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            requests.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return RawHttp.get(fleet.url(), "/stock", Duration.ofSeconds(10));
                } catch (Exception e) {
                    return -1;
                }
            }, CLIENTS));
        }
        return requests.stream().map(CompletableFuture::join).toList();
    }
}
