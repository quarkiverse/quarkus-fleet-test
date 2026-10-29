package org.acme.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;

// The fix: one configured encryption key, and a login on any instance is valid on every other.
@QuarkusFleetTest(instances = 2)
@TestProfile(SharedKeyProfile.class)
class LoginSharedAcrossInstancesIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theCookieOfOneInstanceIsAcceptedByTheOther() {
        String cookie = Login.through(fleet, 0);
        assertThat(Login.accountStatusThrough(fleet, 1, cookie)).isEqualTo(200);
    }
}
