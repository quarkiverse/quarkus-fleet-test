package org.acme.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.QuarkusFleetTest;

/**
 * The bug: a {@code @Scheduled} job runs on every instance. One instance, one report per tick; two instances,
 * two. A single-instance test cannot tell the difference.
 */
@QuarkusFleetTest(instances = 2)
class ReportSentTwiceIT {

    @Test
    void theReportGoesOutMoreThanOncePerTick() throws Exception {
        Map<String, Long> summary = ReportsAssertions.afterAFewTicks();
        assertThat(summary.get("ticks")).isGreaterThanOrEqualTo(2);
        assertThat(summary.get("sent")).as("reports sent for %d ticks", summary.get("ticks"))
                .isGreaterThan(summary.get("ticks"));
    }
}
