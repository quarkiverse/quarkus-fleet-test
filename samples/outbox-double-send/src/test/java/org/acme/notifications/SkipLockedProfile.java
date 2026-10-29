package org.acme.notifications;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class SkipLockedProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("outbox.skip-locked", "true");
    }
}
