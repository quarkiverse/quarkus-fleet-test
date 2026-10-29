package io.quarkiverse.fleettest.internal;

import static io.quarkiverse.fleettest.internal.TestSupport.READINESS_OFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Traffic;

class TrafficTest {

    @Test
    void theClientStopsWhenTheActionFails() {
        // a fleet with nothing to route to: every request fails at once
        DefaultFleet fleet = new DefaultFleet(List.of(), READINESS_OFF);
        try {
            assertThatThrownBy(() -> Traffic.during(fleet, "/", Duration.ofSeconds(10), () -> {
                throw new IllegalStateException("the action failed");
            })).hasMessage("the action failed");

            assertThat(Thread.getAllStackTraces().keySet()).noneMatch(t -> t.getName().equals("fleet-traffic"));
        } finally {
            fleet.close();
        }
    }
}
