package io.quarkiverse.fleettest.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.FleetInstance;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkiverse.fleettest.RawHttp;

// The same fleet, as containers of the image the build produced.
@QuarkusFleetTest(instances = 2)
class ContainerFleetIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void roundRobinReachesEveryContainer() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            seen.add(servingContainer());
        }
        assertThat(seen).containsExactlyInAnyOrder("0", "1");
    }

    @Test
    void pinRoutesToOneContainer() {
        fleet.pin(0);
        for (int i = 0; i < 5; i++) {
            assertThat(servingContainer()).isEqualTo("0");
        }
    }

    @Test
    void aKilledContainerIsGone() {
        fleet.instance(0).kill();
        assertThat(fleet.instance(0).isRunning()).isFalse();
        for (int i = 0; i < 5; i++) {
            assertThat(servingContainer()).isEqualTo("1");
        }
        fleet.instance(0).start();
        assertThat(fleet.instance(0).isRunning()).isTrue();
    }

    @Test
    void aPausedContainerDoesNotAnswerUntilResumed() throws Exception {
        FleetInstance zero = fleet.instance(0);
        zero.pause();
        assertThatThrownBy(() -> RawHttp.get(zero.url(), "/instance", Duration.ofMillis(500)))
                .isInstanceOf(SocketTimeoutException.class);
        zero.resume();
        assertThat(RawHttp.get(zero.url(), "/instance", Duration.ofSeconds(2))).isEqualTo(200);
    }

    private static String servingContainer() {
        return given().get("/instance").then().statusCode(200).extract().body().asString();
    }
}
