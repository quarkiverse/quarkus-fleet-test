package org.acme.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

/**
 * The bug: the OIDC state cookie is encrypted with a key each instance generated at startup, because the client
 * secret is too short to derive one from and no state secret is configured. The browser is sent to Keycloak by one
 * instance and comes back, through the load balancer, to another, which cannot read the cookie: the login fails
 * although the user just authenticated. On one instance the flow always completes.
 */
@QuarkusFleetTest(instances = 2)
class LoginFailsOnOtherInstanceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theRedirectBackFromKeycloakIsRejectedByTheOtherInstance() {
        CodeFlow.Started started = CodeFlow.startThrough(fleet, 0);
        String callback = CodeFlow.loginAtKeycloak(started);
        // the other instance cannot decrypt the state cookie, so it cannot finish the flow
        assertThat(CodeFlow.finishThrough(fleet, 1, started, callback).statusCode()).isEqualTo(401);
    }
}
