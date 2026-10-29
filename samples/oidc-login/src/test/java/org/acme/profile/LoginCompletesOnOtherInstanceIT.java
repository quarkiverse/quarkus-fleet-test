package org.acme.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;

// The fix: one configured state secret, and the redirect back from Keycloak can land on any instance.
@QuarkusFleetTest(instances = 2)
@TestProfile(StateSecretProfile.class)
class LoginCompletesOnOtherInstanceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theRedirectBackFromKeycloakIsAcceptedByTheOtherInstance() {
        CodeFlow.Started started = CodeFlow.startThrough(fleet, 0);
        String callback = CodeFlow.loginAtKeycloak(started);

        Response finished = CodeFlow.finishThrough(fleet, 1, started, callback);
        assertThat(finished.statusCode()).isEqualTo(302);
        assertThat(finished.header("Location")).endsWith("/profile");

        // and the session it opened is good on the first instance too
        Response profile = CodeFlow.profileThrough(fleet, 0, finished.cookies());
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(profile.body().asString()).isEqualTo("alice");
    }
}
