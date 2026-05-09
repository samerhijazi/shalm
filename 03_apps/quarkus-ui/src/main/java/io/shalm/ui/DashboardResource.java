package io.shalm.ui;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/")
public class DashboardResource {

    private static final List<String> ACCOUNTS = List.of("org1", "org2", "org3");

    @Inject
    Template dashboard;

    @RestClient
    ApiClient apiClient;

    @Inject
    TransactionStore txStore;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        return dashboard
                .data("balances", fetchBalances())
                .data("accounts", ACCOUNTS)
                .data("transactions", txStore.getAll())
                .data("message", "")
                .data("error", "");
    }

    @POST
    @Path("/transfer")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response transfer(
            @FormParam("from") String from,
            @FormParam("to") String to,
            @FormParam("amount") int amount) {

        String message = "";
        String error = "";

        try {
            TransferResponse resp = apiClient.transfer(new TransferRequest(from, to, amount));
            txStore.add(new TransactionRecord(resp.transactionId, from, to, amount, resp.status));
            if ("success".equals(resp.status)) {
                message = "Transfer successful — TX: " + resp.transactionId;
            } else {
                error = "Transfer failed: " + resp.message;
            }
        } catch (Exception e) {
            error = "API error: " + e.getMessage();
        }

        return Response.ok(dashboard
                .data("balances", fetchBalances())
                .data("accounts", ACCOUNTS)
                .data("transactions", txStore.getAll())
                .data("message", message)
                .data("error", error))
                .build();
    }

    private Map<String, Integer> fetchBalances() {
        Map<String, Integer> balances = new LinkedHashMap<>();
        for (String acc : ACCOUNTS) {
            try {
                balances.put(acc, apiClient.getBalance(acc).balance);
            } catch (Exception e) {
                balances.put(acc, -1);
            }
        }
        return balances;
    }
}
