package io.shalm;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class FabricLedgerResourceTest {

    @Test
    void getBlocksExplainsWhenFabricIsDisabled() {
        given()
                .when().get("/fabric/blocks")
                .then()
                .statusCode(503)
                .body("status", equalTo("down"))
                .body("error", equalTo("Fabric ledger not available"));
    }

    @Test
    void getBlockExplainsWhenFabricIsDisabled() {
        given()
                .when().get("/fabric/blocks/0")
                .then()
                .statusCode(503)
                .body("status", equalTo("down"))
                .body("error", equalTo("Fabric ledger not available"));
    }
}
