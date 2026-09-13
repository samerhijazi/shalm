package io.shalm;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class TransferResourceTest {

    private static final String FROM = "ACC-TEST-TX-FROM";
    private static final String TO   = "ACC-TEST-TX-TO";

    @AfterEach
    void cleanUp() {
        given().when().delete("/accounts/" + FROM);
        given().when().delete("/accounts/" + TO);
    }

    private void createAccount(String id, int balance) {
        given()
          .contentType("application/json")
          .body("{\"id\":\"" + id + "\",\"owner\":\"Tester\",\"bank\":\"Bank1\",\"initialBalance\":" + balance + "}")
          .when().post("/accounts")
          .then()
             .statusCode(201);
    }

    @Test
    void transfer_success() {
        createAccount(FROM, 500);
        createAccount(TO, 100);

        given()
          .contentType("application/json")
          .body("{\"from\":\"" + FROM + "\",\"to\":\"" + TO + "\",\"amount\":200}")
          .when().post("/transfer")
          .then()
             .statusCode(200)
             .body("status", equalTo("success"));

        given().when().get("/balance/" + FROM).then().body("balance", equalTo(300));
        given().when().get("/balance/" + TO).then().body("balance", equalTo(300));
    }

    @Test
    void transfer_insufficientFunds() {
        createAccount(FROM, 10);
        createAccount(TO, 0);

        given()
          .contentType("application/json")
          .body("{\"from\":\"" + FROM + "\",\"to\":\"" + TO + "\",\"amount\":100}")
          .when().post("/transfer")
          .then()
             .statusCode(422)
             .body("status", equalTo("failed"));
    }

    @Test
    void transfer_unknownAccount() {
        given()
          .contentType("application/json")
          .body("{\"from\":\"NOPE-1\",\"to\":\"NOPE-2\",\"amount\":10}")
          .when().post("/transfer")
          .then()
             .statusCode(422);
    }

    @Test
    void transfer_invalidAmountBadRequest() {
        createAccount(FROM, 100);
        createAccount(TO, 0);

        given()
          .contentType("application/json")
          .body("{\"from\":\"" + FROM + "\",\"to\":\"" + TO + "\",\"amount\":0}")
          .when().post("/transfer")
          .then()
             .statusCode(400)
             .body("status", equalTo("invalid"));
    }
}
