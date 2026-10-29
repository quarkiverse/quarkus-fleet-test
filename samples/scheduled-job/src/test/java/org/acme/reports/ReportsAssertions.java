package org.acme.reports;

import static io.restassured.RestAssured.given;

import java.util.Map;

import io.restassured.common.mapper.TypeRef;

final class ReportsAssertions {

    private ReportsAssertions() {
    }

    // Lets the job tick a few times on every instance, then reads the counts through the load balancer.
    static Map<String, Long> afterAFewTicks() throws InterruptedException {
        Thread.sleep(3500);
        return given().get("/reports")
                .then()
                .statusCode(200)
                .extract()
                .as(new TypeRef<>() {
                });
    }
}
