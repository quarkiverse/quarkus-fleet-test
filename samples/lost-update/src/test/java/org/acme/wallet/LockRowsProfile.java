package org.acme.wallet;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class LockRowsProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("wallet.lock-rows", "true");
    }
}
