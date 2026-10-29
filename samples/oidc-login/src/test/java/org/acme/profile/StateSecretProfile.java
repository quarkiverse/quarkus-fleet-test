package org.acme.profile;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class StateSecretProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.oidc.authentication.state-secret", "the-same-state-secret-on-every-instance");
    }
}
