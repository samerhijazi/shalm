package io.shalm;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class BalanceResourceTest {

    @Test
    void getBalance_found() {
        given()
          .when().get("/balance/ACC-B2-002")
          .then()
             .statusCode(200)
             .body("id", equalTo("ACC-B2-002"))
             .body("balance", equalTo(500));
    }

    @Test
    void getBalance_notFound() {
        given()
          .when().get("/balance/DOES-NOT-EXIST")
          .then()
             .statusCode(404);
    }
}
