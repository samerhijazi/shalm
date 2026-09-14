package io.shalm;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class NetworkStatusResourceTest {

    @Test
    void reportsHonestStateWhenFabricIsDisabled() {
        given()
                .when().get("/fabric/network-status")
                .then()
                .statusCode(200)
                .body("fabricEnabled", equalTo(false))
                .body("fabricAvailable", equalTo(false))
                .body("latestBlockNumber", nullValue())
                .body("channel", equalTo("mychannel"))
                .body("chaincodeName", equalTo("bank-transfer"))
                .body("lastCheckedTimestamp", notNullValue());
    }
}
