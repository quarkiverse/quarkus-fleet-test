package org.acme.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;

// The fix: each run claims its tick in the shared database first, so the fleet sends one report per tick.
@QuarkusFleetTest(instances = 2)
@TestProfile(ClaimTickProfile.class)
class ReportSentOnceIT {

    @Test
    void theReportGoesOutOncePerTick() throws Exception {
        Map<String, Long> summary = ReportsAssertions.afterAFewTicks();
        assertThat(summary.get("ticks")).isGreaterThanOrEqualTo(2);
        assertThat(summary.get("sent")).isEqualTo(summary.get("ticks"));
    }
}
