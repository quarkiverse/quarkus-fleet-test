package io.quarkiverse.fleettest.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkiverse.fleettest.RawHttp;
import io.quarkiverse.fleettest.Readiness;

// The load balancer follows each instance's readiness like a kubelet and kube-proxy would.
@QuarkusFleetTest(instances = 2, readiness = @Readiness(path = "/q/health/ready", intervalMillis = 100, failureThreshold = 2))
class ReadinessIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void anUnreadyInstanceLeavesRotationAndComesBack() throws Exception {
        assertThat(fleet.readyInstances()).hasSize(2);
        assertThat(fleet.instance(0).readinessTransitions()).isZero();

        Instances.setReady(fleet.instance(0), false);
        await().atMost(Duration.ofSeconds(2)).until(() -> !fleet.instance(0).isReady());
        assertThat(fleet.readyInstances()).containsExactly(fleet.instance(1));

        // nothing goes to 0 any more, even when pinned to it: there is nothing ready to route to
        fleet.pin(0);
        assertThatThrownBy(() -> RawHttp.get(fleet.url(), "/instance")).isInstanceOf(IOException.class);
        fleet.roundRobin();
        for (int i = 0; i < 4; i++) {
            assertThat(Instances.serving()).isEqualTo("1");
        }

        Instances.setReady(fleet.instance(0), true);
        await().atMost(Duration.ofSeconds(2)).until(() -> fleet.instance(0).isReady());
        assertThat(fleet.readyInstances()).hasSize(2);
        assertThat(fleet.instance(0).readinessTransitions()).isEqualTo(2);
    }

    @Test
    void withNoReadyInstanceTheBalancerRefuses() throws Exception {
        Instances.setAllReady(fleet, false);
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> fleet.readyInstances().isEmpty());
            assertThatThrownBy(() -> RawHttp.get(fleet.url(), "/instance")).isInstanceOf(IOException.class);
        } finally {
            Instances.setAllReady(fleet, true);
            await().atMost(Duration.ofSeconds(2)).until(() -> fleet.readyInstances().size() == 2);
        }
    }
}
