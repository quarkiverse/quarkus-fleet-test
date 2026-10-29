package io.quarkiverse.fleettest.internal;

import io.quarkiverse.fleettest.Readiness;

/**
 * The readiness settings the load balancer works from, decoupled from the {@link Readiness} annotation that
 * supplies them: the balancer takes plain values, not the annotation. Readiness is off when the path is empty.
 */
record ReadinessSettings(String path, long intervalMillis, int failureThreshold, int successThreshold,
        long deregistrationLagMillis, boolean failOpen) {

    ReadinessSettings {
        if (!path.isEmpty() && !path.startsWith("/")) {
            throw new IllegalArgumentException("@Readiness(path) must start with '/', got '" + path + "'");
        }
        if (intervalMillis < 1) {
            throw new IllegalArgumentException("@Readiness(intervalMillis) must be at least 1, got " + intervalMillis);
        }
        if (failureThreshold < 1) {
            throw new IllegalArgumentException(
                    "@Readiness(failureThreshold) must be at least 1, got " + failureThreshold);
        }
        if (successThreshold < 1) {
            throw new IllegalArgumentException(
                    "@Readiness(successThreshold) must be at least 1, got " + successThreshold);
        }
        if (deregistrationLagMillis < 0) {
            throw new IllegalArgumentException(
                    "@Readiness(deregistrationLagMillis) must not be negative, got " + deregistrationLagMillis);
        }
    }

    boolean enabled() {
        return !path.isEmpty();
    }

    static ReadinessSettings from(Readiness readiness) {
        return new ReadinessSettings(readiness.path(), readiness.intervalMillis(), readiness.failureThreshold(),
                readiness.successThreshold(), readiness.deregistrationLagMillis(), readiness.failOpen());
    }
}
