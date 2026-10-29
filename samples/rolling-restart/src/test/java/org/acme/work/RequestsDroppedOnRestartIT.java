package org.acme.work;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkiverse.fleettest.Readiness;
import io.quarkiverse.fleettest.Traffic;

/**
 * The bug: Kubernetes sends SIGTERM and removes the endpoint at the same time, and proxies act on the removal a
 * little later. An instance that stops listening at once leaves every request routed to it in that window with a
 * connection error. The balancer here keeps routing for one second after the stop, as kube-proxy would.
 */
@QuarkusFleetTest(instances = 2, readiness = @Readiness(path = "/q/health/ready", intervalMillis = 100, failureThreshold = 1, deregistrationLagMillis = 1000))
class RequestsDroppedOnRestartIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void stoppingAnInstanceFailsTheRequestsStillRoutedToIt() throws Exception {
        // a client that never stops sending requests, and a stop of one instance in the middle
        Traffic.Outcome outcome = Traffic.during(fleet, "/work", Duration.ofSeconds(3), () -> fleet.instance(0).stop());
        assertThat(outcome.succeeded()).isGreaterThan(0);
        assertThat(outcome.failed()).as("requests that hit the stopped instance").isGreaterThan(0);
    }
}
