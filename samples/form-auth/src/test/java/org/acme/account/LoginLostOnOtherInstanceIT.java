package org.acme.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

/**
 * The bug: form authentication without an encryption key. Each instance encrypts the login cookie with a key it
 * generated at startup, so a user logged in on one instance is anonymous on the next. In production that is a
 * login loop whenever the load balancer is not sticky; on one instance it never shows.
 */
@QuarkusFleetTest(instances = 2)
class LoginLostOnOtherInstanceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theCookieOfOneInstanceIsRejectedByTheOther() {
        String cookie = Login.through(fleet, 0);
        assertThat(Login.accountStatusThrough(fleet, 0, cookie)).isEqualTo(200);
        // the other instance cannot decrypt it and sends the user back to the login page
        assertThat(Login.accountStatusThrough(fleet, 1, cookie)).isEqualTo(302);
    }
}
