package org.acme.chat;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class RedisRelayProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("chat.relay", "redis");
    }
}
