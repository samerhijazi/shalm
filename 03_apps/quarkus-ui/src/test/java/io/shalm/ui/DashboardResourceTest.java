package io.shalm.ui;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class DashboardResourceTest {

    @InjectMock
    @RestClient
    ApiClient apiClient;

    @BeforeEach
    void setUp() {
        when(apiClient.getAllAccounts()).thenReturn(List.of(
                accountInfo("ACC-B1-001", "ClientA", "Bank1", 1000),
                accountInfo("ACC-B2-001", "ClientB", "Bank2", 1000)));
        when(apiClient.getFabricBalance(anyString())).thenThrow(new RuntimeException("fabric disabled"));
    }

    @Test
    void index_rendersAccountsFromApi() {
        given()
          .when().get("/")
          .then()
             .statusCode(200)
             .body(containsString("ClientA"))
             .body(containsString("ClientB"));
    }

    @Test
    void index_showsApiTestSummaryWhenAvailable() {
        TestSummary summary = new TestSummary();
        summary.app = "quarkus-api";
        summary.tests = 10;
        summary.passed = 10;
        summary.timestamp = "2026-09-13T22:00:00Z";
        summary.commit = "abc1234";
        when(apiClient.getApiTestResults()).thenReturn(summary);

        given()
          .when().get("/")
          .then()
             .statusCode(200)
             .body(containsString("10 / 10 passed"));
    }

    @Test
    void transfer_successUpdatesHistoryAndShowsMessage() {
        TransferResponse resp = new TransferResponse();
        resp.transactionId = "11111111-2222-3333-4444-555555555555";
        resp.status = "success";
        resp.message = "Transfer completed";
        when(apiClient.transfer(any())).thenReturn(resp);

        given()
          .contentType("application/x-www-form-urlencoded")
          .formParam("from", "ACC-B1-001")
          .formParam("to", "ACC-B2-001")
          .formParam("amount", "100")
          .when().post("/transfer")
          .then()
             .statusCode(200)
             .body(containsString("Transfer successful"));
    }

    @Test
    void transfer_apiFailureShowsError() {
        when(apiClient.transfer(any())).thenThrow(new RuntimeException("boom"));

        given()
          .contentType("application/x-www-form-urlencoded")
          .formParam("from", "ACC-B1-001")
          .formParam("to", "ACC-B2-001")
          .formParam("amount", "100")
          .when().post("/transfer")
          .then()
             .statusCode(200)
             .body(containsString("API error"));
    }

    @Test
    void deleteAccount_success() {
        when(apiClient.deleteAccount(anyString())).thenReturn(Response.noContent().build());

        given()
          .contentType("application/x-www-form-urlencoded")
          .formParam("id", "ACC-B1-001")
          .when().post("/manage/delete")
          .then()
             .statusCode(200)
             .body(containsString("deleted"));
    }

    private static AccountInfo accountInfo(String id, String owner, String bank, int balance) {
        AccountInfo a = new AccountInfo();
        a.id = id;
        a.owner = owner;
        a.bank = bank;
        a.balance = balance;
        return a;
    }
}
