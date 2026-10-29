package io.quarkiverse.fleettest.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReadinessSettingsTest {

    @Test
    void settingsTheBalancerCannotWorkFromAreRejected() {
        assertThatThrownBy(() -> new ReadinessSettings("q/health/ready", 250, 3, 1, 0, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("path");
        assertThatThrownBy(() -> new ReadinessSettings("/q/health/ready", 0, 3, 1, 0, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("intervalMillis");
        assertThatThrownBy(() -> new ReadinessSettings("/q/health/ready", 250, 0, 1, 0, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("failureThreshold");
        assertThatThrownBy(() -> new ReadinessSettings("/q/health/ready", 250, 3, 0, 0, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("successThreshold");
        assertThatThrownBy(() -> new ReadinessSettings("/q/health/ready", 250, 3, 1, -1, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("deregistrationLagMillis");
    }
}
