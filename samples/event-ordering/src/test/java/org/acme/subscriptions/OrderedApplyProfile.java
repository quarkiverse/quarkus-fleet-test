package org.acme.subscriptions;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class OrderedApplyProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("events.ordered-apply", "true");
    }
}
