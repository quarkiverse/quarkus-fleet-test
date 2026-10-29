package org.acme.inventory;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class SizedPerReplicaProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        // the database's 12 connections divided by the 2 replicas
        return Map.of(
                "quarkus.datasource.jdbc.min-size", "6",
                "quarkus.datasource.jdbc.max-size", "6",
                "quarkus.datasource.jdbc.initial-size", "6");
    }
}
