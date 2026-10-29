package org.acme.imports;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ResumableProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("imports.resumable", "true");
    }
}
