package org.acme.orders;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class IdempotentProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("orders.idempotent", "true");
    }
}
