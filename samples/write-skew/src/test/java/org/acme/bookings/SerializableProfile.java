package org.acme.bookings;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class SerializableProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        // Runtime datasource config, so it reaches every packaged instance. This is the whole fix: no code changes.
        return Map.of("quarkus.datasource.jdbc.transaction-isolation-level", "serializable");
    }
}
