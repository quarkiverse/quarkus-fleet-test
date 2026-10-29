package org.acme.work;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ShutdownDelayProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        // keep serving for two seconds after SIGTERM while reporting unready: longer than the propagation delay
        return Map.of("quarkus.shutdown.delay", "PT2S");
    }
}
