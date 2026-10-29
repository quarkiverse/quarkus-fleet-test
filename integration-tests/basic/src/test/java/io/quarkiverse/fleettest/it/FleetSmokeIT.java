package io.quarkiverse.fleettest.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

@QuarkusFleetTest(instances = 2)
class FleetSmokeIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void roundRobinReachesEveryInstance() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            seen.add(Instances.serving());
        }
        assertThat(seen).containsExactlyInAnyOrder("0", "1");
    }

    @Test
    void pinRoutesEveryConnectionToOneInstance() {
        fleet.pin(1);
        for (int i = 0; i < 5; i++) {
            assertThat(Instances.serving()).isEqualTo("1");
        }
    }

    @Test
    void instanceUrlBypassesTheLoadBalancer() {
        String index = given().baseUri(fleet.instance(0).url().toString()).get("/instance").then().statusCode(200)
                .extract().body().asString();
        assertThat(index).isEqualTo("0");
    }

    @Test
    void stoppedInstanceReceivesNoTraffic() {
        fleet.instance(0).stop();
        try {
            for (int i = 0; i < 5; i++) {
                assertThat(Instances.serving()).isEqualTo("1");
            }
        } finally {
            fleet.instance(0).start();
        }
        assertThat(fleet.instance(0).isRunning()).isTrue();
    }
}
