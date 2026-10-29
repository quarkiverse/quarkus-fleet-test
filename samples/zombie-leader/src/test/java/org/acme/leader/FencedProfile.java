package org.acme.leader;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class FencedProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("lease.fenced", "true");
    }
}
