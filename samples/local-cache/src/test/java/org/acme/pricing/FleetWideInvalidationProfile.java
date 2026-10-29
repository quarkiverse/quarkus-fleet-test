package org.acme.pricing;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class FleetWideInvalidationProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("pricing.invalidate-fleet-wide", "true");
    }
}
