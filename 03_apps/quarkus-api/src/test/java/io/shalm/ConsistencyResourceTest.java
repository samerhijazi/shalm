package io.shalm;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class ConsistencyResourceTest {

    @Test
    void reportsUnavailableFabricForEverySeededAccount() {
        given()
                .when().get("/consistency")
                .then()
                .statusCode(200)
                .body("fabricAvailable", equalTo(false))
                .body("totalChecked", equalTo(4))
                .body("mismatchCount", equalTo(0))
                .body("entries", hasSize(4))
                .body("entries.match", everyItem(equalTo(false)))
                .body("entries.fabricBalance", everyItem(equalTo(null)))
                .body("entries.fabricError", everyItem(equalTo("Fabric ledger not available")));
    }
}
