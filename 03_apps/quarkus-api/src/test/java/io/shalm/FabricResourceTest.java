package io.shalm;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class FabricResourceTest {

    @Test
    void healthExplainsWhenFabricIsDisabled() {
        given()
                .when().get("/fabric/health")
                .then()
                .statusCode(503)
                .body("status", equalTo("down"))
                .body("error", equalTo("Fabric ledger not available"));
    }

    @Test
    void disabledFabricDoesNotMakeLocalDevelopmentUnready() {
        given()
                .when().get("/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
