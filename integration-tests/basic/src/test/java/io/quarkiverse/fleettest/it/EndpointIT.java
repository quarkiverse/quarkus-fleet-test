package io.quarkiverse.fleettest.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.common.http.TestHTTPEndpoint;

// @TestHTTPEndpoint sets RestAssured's base path, as in any other Quarkus test.
@QuarkusFleetTest
@TestHTTPEndpoint(InstanceResource.class)
class EndpointIT {

    @Test
    void requestsAreRelativeToTheEndpoint() {
        assertThat(given().get().asString()).isIn("0", "1");
        assertThat(given().get("/greeting").asString()).isEqualTo("none");
    }
}
