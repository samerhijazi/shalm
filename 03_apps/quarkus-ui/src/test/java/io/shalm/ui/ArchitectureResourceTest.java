package io.shalm.ui;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class ArchitectureResourceTest {

    @Test
    void index_rendersArchitecturePage() {
        given()
          .when().get("/architecture")
          .then()
             .statusCode(200)
             .body(containsString("Quarkus API"))
             .body(containsString("ArgoCD"));
    }
}
