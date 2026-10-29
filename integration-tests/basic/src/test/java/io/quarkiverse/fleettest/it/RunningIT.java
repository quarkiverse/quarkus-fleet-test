package io.quarkiverse.fleettest.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

// A fleet that starts smaller than its size, for the test to grow when it wants to watch a replica join.
@QuarkusFleetTest(instances = 2, running = 1)
class RunningIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void instancesBeyondRunningStartWhenTheTestSaysSo() {
        assertThat(fleet.instance(0).isRunning()).isTrue();
        assertThat(fleet.instance(1).isRunning()).isFalse();
        for (int i = 0; i < 5; i++) {
            assertThat(Instances.serving()).isEqualTo("0");
        }

        fleet.instance(1).start();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            seen.add(Instances.serving());
        }
        assertThat(seen).containsExactlyInAnyOrder("0", "1");
    }
}
