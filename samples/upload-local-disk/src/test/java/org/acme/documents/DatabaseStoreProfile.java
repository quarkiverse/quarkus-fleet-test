package org.acme.documents;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class DatabaseStoreProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("documents.store", "database");
    }
}
