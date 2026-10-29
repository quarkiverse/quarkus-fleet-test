package org.acme.reports;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ClaimTickProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("report.claim-tick", "true");
    }
}
