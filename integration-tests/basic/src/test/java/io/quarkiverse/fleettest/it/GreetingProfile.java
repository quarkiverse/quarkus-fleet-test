package io.quarkiverse.fleettest.it;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class GreetingProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("greeting", "hello from the profile");
    }
}
