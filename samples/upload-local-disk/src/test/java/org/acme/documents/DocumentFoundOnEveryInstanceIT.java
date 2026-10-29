package org.acme.documents;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;

/**
 * The fix: {@code documents.store=database}, storage every instance shares. The document uploaded through one
 * instance is served by the other.
 */
@QuarkusFleetTest(instances = 2)
@TestProfile(DatabaseStoreProfile.class)
class DocumentFoundOnEveryInstanceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theDocumentUploadedThroughOneInstanceIsServedByTheOther() {
        fleet.pin(0);
        Response upload = given().multiPart("file", "hello.txt", "hello".getBytes())
                .post("/documents");
        assertThat(upload.statusCode()).isEqualTo(200);
        String id = upload.body().asString();

        fleet.pin(1);
        Response download = given().get("/documents/" + id);
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body().asByteArray()).isEqualTo("hello".getBytes());
    }
}
