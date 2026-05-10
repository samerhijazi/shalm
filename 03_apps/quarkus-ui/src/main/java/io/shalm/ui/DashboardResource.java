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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/")
public class DashboardResource {

    @Inject
    Template dashboard;

    @RestClient
    ApiClient apiClient;

    @Inject
    TransactionStore txStore;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        List<AccountInfo> accounts = fetchAccounts();
        return dashboard
                .data("banks",        groupByBank(accounts))
                .data("accounts",     accounts)
                .data("transactions", txStore.getAll())
                .data("message",      "")
                .data("error",        "");
    }

    @POST
    @Path("/transfer")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response transfer(
            @FormParam("from") String fromId,
            @FormParam("to")   String toId,
            @FormParam("amount") int amount) {

        String message = "";
        String error   = "";

        List<AccountInfo> accounts = fetchAccounts();
        Map<String, AccountInfo> accountMap = accounts.stream()
                .collect(Collectors.toMap(a -> a.id, a -> a));

        try {
            TransferResponse resp = apiClient.transfer(new TransferRequest(fromId, toId, amount));

            AccountInfo fromAcc = accountMap.get(fromId);
            AccountInfo toAcc   = accountMap.get(toId);
            String fromLabel = fromAcc != null ? fromAcc.owner + " (" + fromAcc.bank + ")" : fromId;
            String toLabel   = toAcc   != null ? toAcc.owner   + " (" + toAcc.bank   + ")" : toId;

            txStore.add(new TransactionRecord(
                    resp.transactionId,
                    fromId, fromLabel,
                    toId,   toLabel,
                    amount, resp.status));

            if ("success".equals(resp.status)) {
                message = "Transfer successful — TX: " + resp.transactionId;
            } else {
                error = "Transfer failed: " + resp.message;
            }
        } catch (Exception e) {
            error = "API error: " + e.getMessage();
        }

        accounts = fetchAccounts();
        return Response.ok(dashboard
                .data("banks",        groupByBank(accounts))
                .data("accounts",     accounts)
                .data("transactions", txStore.getAll())
                .data("message",      message)
                .data("error",        error))
                .build();
    }

    private List<AccountInfo> fetchAccounts() {
        try {
            return apiClient.getAllAccounts();
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, List<AccountInfo>> groupByBank(List<AccountInfo> accounts) {
        Map<String, List<AccountInfo>> result = new LinkedHashMap<>();
        for (AccountInfo acc : accounts) {
            result.computeIfAbsent(acc.bank, k -> new ArrayList<>()).add(acc);
        }
        return result;
    }
}
