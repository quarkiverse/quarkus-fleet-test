package io.quarkiverse.fleettest.it;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

// A readiness check a test can flip, to drive the load balancer's view of this instance.
@Readiness
@ApplicationScoped
public class ToggleableReadiness implements HealthCheck {

    private final AtomicBoolean ready = new AtomicBoolean(true);

    // A method, not a field: callers hold a client proxy, and a field written through it never reaches the bean.
    public void ready(boolean value) {
        ready.set(value);
    }

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("toggle").status(ready.get()).build();
    }
}
