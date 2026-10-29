package io.quarkiverse.fleettest.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;

// The config overrides of a @TestProfile reach every instance; the fleet is separate from other classes'.
@QuarkusFleetTest(instances = 2)
@TestProfile(GreetingProfile.class)
class ProfiledFleetIT {

    @Test
    void everyInstanceHasTheProfileConfiguration(Fleet fleet) {
        for (int i = 0; i < fleet.size(); i++) {
            String greeting = given().baseUri(fleet.instance(i).url().toString()).get("/instance/greeting").then()
                    .statusCode(200).extract().body().asString();
            assertThat(greeting).as("instance %d", i).isEqualTo("hello from the profile");
        }
    }
}
