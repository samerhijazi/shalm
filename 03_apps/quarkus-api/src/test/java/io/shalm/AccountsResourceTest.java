package io.shalm;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
class AccountsResourceTest {

    @Test
    void listAccounts_includesSeededAccounts() {
        given()
          .when().get("/accounts")
          .then()
             .statusCode(200)
             .body("id", hasItem("ACC-B1-001"));
    }

    @Test
    void getAccount_found() {
        given()
          .when().get("/accounts/ACC-B1-001")
          .then()
             .statusCode(200)
             .body("owner", equalTo("ClientA"))
             .body("bank", equalTo("Bank1"));
    }

    @Test
    void getAccount_notFound() {
        given()
          .when().get("/accounts/DOES-NOT-EXIST")
          .then()
             .statusCode(404);
    }

    @Test
    void createAndDeleteAccount_lifecycle() {
        given()
          .contentType("application/json")
          .body("{\"id\":\"ACC-TEST-CREATE\",\"owner\":\"Tester\",\"bank\":\"Bank1\",\"initialBalance\":250}")
          .when().post("/accounts")
          .then()
             .statusCode(201);

        given()
          .when().delete("/accounts/ACC-TEST-CREATE")
          .then()
             .statusCode(204);

        given()
          .when().get("/accounts/ACC-TEST-CREATE")
          .then()
             .statusCode(404);
    }

    @Test
    void createAccount_duplicateIdConflict() {
        given()
          .contentType("application/json")
          .body("{\"id\":\"ACC-B1-001\",\"owner\":\"X\",\"bank\":\"Bank1\",\"initialBalance\":0}")
          .when().post("/accounts")
          .then()
             .statusCode(409);
    }

    @Test
    void createAccount_missingFieldsBadRequest() {
        given()
          .contentType("application/json")
          .body("{}")
          .when().post("/accounts")
          .then()
             .statusCode(400);
    }
}
