package io.shalm;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class OwnershipResourceTest {

    @Test
    void listExplainsWhenFabricIsDisabled() {
        given()
                .when().get("/fabric/assets")
                .then()
                .statusCode(503)
                .body("status", equalTo("down"));
    }

    @Test
    void transferRequiresBothOrganizationGateways() {
        given()
                .contentType("application/json")
                .body("{\"newOwner\":\"Bank2 Trade Desk\",\"newOwnerMsp\":\"Org2MSP\"}")
                .when().post("/fabric/assets/ASSET-001/transfer")
                .then()
                .statusCode(503)
                .body("status", equalTo("down"));
    }
}
