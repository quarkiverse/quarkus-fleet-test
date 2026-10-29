package org.acme.quotes;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class SharedLimitProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quotes.limit.shared", "true");
    }
}
