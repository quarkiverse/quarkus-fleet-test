package org.acme.accounts;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class LockOrderByIdProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("transfer.lock-order-by-id", "true");
    }
}
