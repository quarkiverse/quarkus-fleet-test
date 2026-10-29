package org.acme.documents;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.restassured.response.Response;

/**
 * The bug: the upload is copied to the local disk of the instance that received it. Every instance has a disk of
 * its own, as every pod does, so the next request for the document, balanced onto another instance, finds
 * nothing: the document is there on one refresh and gone on the next. On one instance uploads and downloads
 * always agree.
 */
@QuarkusFleetTest(instances = 2)
class DocumentMissingOnOtherInstanceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theDocumentUploadedThroughOneInstanceIsMissingOnTheOther() {
        fleet.pin(0);
        Response upload = given().multiPart("file", "hello.txt", "hello".getBytes())
                .post("/documents");
        assertThat(upload.statusCode()).isEqualTo(200);
        String id = upload.body().asString();

        // the file is under the temp directory of instance 0, which instance 1 cannot see
        fleet.pin(1);
        assertThat(given().get("/documents/" + id).statusCode()).isEqualTo(404);

        // the instance that stored it still has it
        fleet.pin(0);
        Response download = given().get("/documents/" + id);
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body().asByteArray()).isEqualTo("hello".getBytes());
    }
}
