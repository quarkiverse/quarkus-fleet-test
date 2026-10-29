package org.acme.payments;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class JdbcStoreProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.idempotency.store", "jdbc");
    }
}
