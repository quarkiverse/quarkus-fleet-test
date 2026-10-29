package io.quarkiverse.fleettest.internal;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * What the load balancer did during one test, with timestamps, so that a failure can be lined up against the
 * instances' own logs: a kill here, a readiness probe flipping there. The wall-clock time matches the
 * timestamps in the instance logs; the offset is from the first event of the test.
 */
final class Timeline {

    private record Event(long atMillis, String message) {
    }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final List<Event> events = new ArrayList<>();
    private long startMillis;

    synchronized void record(String message) {
        long now = System.currentTimeMillis();
        if (events.isEmpty()) {
            startMillis = now;
        }
        events.add(new Event(now, message));
    }

    // Forgets the previous test's events, so each test's timeline is its own.
    synchronized void clear() {
        events.clear();
    }

    synchronized boolean isEmpty() {
        return events.isEmpty();
    }

    synchronized String describe() {
        StringBuilder sb = new StringBuilder();
        for (Event event : events) {
            double offsetSeconds = (event.atMillis() - startMillis) / 1000.0;
            sb.append("    ").append(CLOCK.format(Instant.ofEpochMilli(event.atMillis())))
                    .append(String.format(" (+%6.3fs) ", offsetSeconds))
                    .append(event.message()).append('\n');
        }
        return sb.toString();
    }
}
