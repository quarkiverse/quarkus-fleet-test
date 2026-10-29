package org.acme.account;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class SharedKeyProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.http.auth.session.encryption-key", "the-same-key-on-every-instance");
    }
}
