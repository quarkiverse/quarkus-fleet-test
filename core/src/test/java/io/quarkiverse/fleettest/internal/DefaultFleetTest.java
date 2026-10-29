package io.quarkiverse.fleettest.internal;

import static io.quarkiverse.fleettest.internal.TestSupport.READINESS_OFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.junit.jupiter.api.Test;

class DefaultFleetTest {

    @Test
    void closeStopsEveryInstanceEvenWhenOneFailsToStop() {
        FakeLauncher first = new FakeLauncher(() -> FakeLauncher.listening(1), new IOException("would not stop"));
        FakeLauncher second = FakeLauncher.listeningOn(2);
        DefaultFleet fleet = new DefaultFleet(
                List.of(TestSupport.instance(0, first), TestSupport.instance(1, second)), READINESS_OFF);
        fleet.instance(0).start();
        fleet.instance(1).start();

        assertThatThrownBy(fleet::close).isInstanceOf(UncheckedIOException.class);

        assertThat(first.closed).isTrue();
        assertThat(second.closed).isTrue();
    }
}
