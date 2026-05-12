package io.shalm.ui;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
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
        return render(accounts, "", "", "ledger");
    }

    @POST
    @Path("/transfer")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response transfer(
            @FormParam("from")   String fromId,
            @FormParam("to")     String toId,
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
        return Response.ok(render(accounts, message, error, "transfers")).build();
    }

    @POST
    @Path("/manage/create")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response createAccount(
            @FormParam("id")             String id,
            @FormParam("owner")          String owner,
            @FormParam("bank")           String bank,
            @FormParam("initialBalance") int initialBalance) {

        String message = "";
        String error   = "";

        try {
            AccountInfo created = apiClient.createAccount(
                    new CreateAccountRequest(id.trim(), owner.trim(), bank, initialBalance));
            message = "Account " + created.id + " created for " + created.owner + " (" + created.bank + ")";
        } catch (Exception e) {
            error = "Create failed: " + e.getMessage();
        }

        List<AccountInfo> accounts = fetchAccounts();
        return Response.ok(render(accounts, message, error, "manage")).build();
    }

    @POST
    @Path("/manage/delete")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response deleteAccount(@FormParam("id") String id) {
        String message = "";
        String error   = "";

        try {
            Response apiResp = apiClient.deleteAccount(id);
            if (apiResp.getStatus() == 204) {
                message = "Account " + id + " deleted.";
            } else {
                error = "Delete failed: " + apiResp.readEntity(String.class);
            }
        } catch (Exception e) {
            error = "Delete failed: " + e.getMessage();
        }

        List<AccountInfo> accounts = fetchAccounts();
        return Response.ok(render(accounts, message, error, "manage")).build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TemplateInstance render(List<AccountInfo> accounts,
                                    String message, String error, String activeTab) {
        return dashboard
                .data("banks",        groupByBank(accounts))
                .data("accounts",     accounts)
                .data("ledger",       buildLedger(accounts))
                .data("transactions", txStore.getAll())
                .data("message",      message)
                .data("error",        error)
                .data("activeTab",    activeTab);
    }

    private List<LedgerEntry> buildLedger(List<AccountInfo> accounts) {
        List<LedgerEntry> ledger = new ArrayList<>();
        for (AccountInfo acc : accounts) {
            String fabricBal;
            try {
                BalanceResponse fb = apiClient.getFabricBalance(acc.id);
                fabricBal = String.valueOf(fb.balance);
            } catch (Exception e) {
                fabricBal = "N/A";
            }
            ledger.add(new LedgerEntry(acc, fabricBal));
        }
        return ledger;
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
