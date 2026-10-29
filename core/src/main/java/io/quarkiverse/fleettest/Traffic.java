package io.quarkiverse.fleettest;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A client that sends requests to the fleet back to back for a while, as users do during a rolling restart or a
 * scale-out, and counts how many of them succeeded and how many failed.
 */
public final class Traffic {

    private Traffic() {
    }

    /**
     * What the client saw: the requests answered with 200, and the requests answered with anything else or not
     * answered at all.
     */
    public record Outcome(int succeeded, int failed) {
    }

    /**
     * Sends GET requests for {@code path} through the load balancer, one connection each with {@link RawHttp}, back
     * to back for {@code duration}. Once the first response is in, runs {@code action} (a stop, a start, a kill) and
     * keeps sending until the time is up. A 200 counts as succeeded; any other status, or a connection
     * error, as failed.
     */
    public static Outcome during(Fleet fleet, String path, Duration duration, Runnable action)
            throws InterruptedException {
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        CountDownLatch firstResponse = new CountDownLatch(1);
        long end = System.nanoTime() + duration.toNanos();
        Thread client = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && System.nanoTime() < end) {
                try {
                    if (RawHttp.get(fleet.url(), path) == 200) {
                        succeeded.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                } catch (IOException e) {
                    failed.incrementAndGet();
                }
                firstResponse.countDown();
            }
        }, "fleet-traffic");
        client.start();
        try {
            if (!firstResponse.await(duration.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("no response from the fleet within " + duration);
            }
            action.run();
        } catch (Throwable t) {
            // the client must not outlive a failed action: its requests would land on the next test
            client.interrupt();
            client.join();
            throw t;
        }
        client.join();
        return new Outcome(succeeded.get(), failed.get());
    }
}
