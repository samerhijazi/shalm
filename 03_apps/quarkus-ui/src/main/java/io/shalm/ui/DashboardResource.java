package io.shalm.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Blocking
@Path("/")
public class DashboardResource {

    @Inject
    Template dashboard;

    @Inject
    ObjectMapper objectMapper;

    @RestClient
    ApiClient apiClient;

    @Inject
    TransactionStore txStore;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        List<AccountInfo> accounts = fetchAccounts();
        return render(accounts, "", "", "dashboard", "worldstate", "network");
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
        AccountInfo fromAcc = accountMap.get(fromId);
        AccountInfo toAcc   = accountMap.get(toId);

        if (fromId == null || toId == null || fromId.equals(toId)) {
            error = "Source and destination accounts must be different.";
        } else if (amount <= 0) {
            error = "Amount must be greater than zero.";
        } else if (fromAcc != null && amount > fromAcc.balance) {
            error = "Amount exceeds the available balance.";
        } else {
            try {
                TransferResponse resp = apiClient.transfer(new TransferRequest(fromId, toId, amount));

                String fromLabel = fromAcc != null ? fromAcc.owner + " (" + fromAcc.bank + ")" : fromId;
                String toLabel   = toAcc   != null ? toAcc.owner   + " (" + toAcc.bank   + ")" : toId;

                TransactionRecord record = new TransactionRecord(
                        resp.transactionId, fromId, fromLabel, toId, toLabel, amount, resp.status);
                if ("committed".equals(resp.fabricStatus)) {
                    NetworkStatus ns = fetchNetworkStatus();
                    record.applyFabricMetadata(resp.fabricBlockNumber, resp.fabricValidationCode,
                            ns.channel, ns.chaincodeName);
                }
                txStore.add(record);

                if ("success".equals(resp.status)) {
                    message = "Transfer submitted — TX: " + resp.transactionId + ".";
                    if ("committed".equals(resp.fabricStatus)) {
                        message += " Committed to Fabric block #" + resp.fabricBlockNumber + ".";
                    } else if ("failed".equals(resp.fabricStatus)) {
                        message += " Fabric dual-write failed; the API balance was still updated.";
                    }
                } else {
                    error = "Transfer failed: " + resp.message;
                }
            } catch (Exception e) {
                error = "API error: " + e.getMessage();
            }
        }

        accounts = fetchAccounts();
        return Response.ok(render(accounts, message, error, "transfer", "worldstate", "network")).build();
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

        if (id == null || id.isBlank() || owner == null || owner.isBlank()) {
            error = "Account ID and owner are required.";
        } else {
            try {
                AccountInfo created = apiClient.createAccount(
                        new CreateAccountRequest(id.trim(), owner.trim(), bank, initialBalance));
                message = "Account " + created.id + " created for " + created.owner + " (" + created.bank + ")";
            } catch (Exception e) {
                error = "Create failed: " + e.getMessage();
            }
        }

        List<AccountInfo> accounts = fetchAccounts();
        return Response.ok(render(accounts, message, error, "accounts", "worldstate", "network")).build();
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
                message = "Account " + id + " closed.";
            } else {
                error = "Close failed: " + apiResp.readEntity(String.class);
            }
        } catch (Exception e) {
            error = "Close failed: " + e.getMessage();
        }

        List<AccountInfo> accounts = fetchAccounts();
        return Response.ok(render(accounts, message, error, "accounts", "worldstate", "network")).build();
    }

    @POST
    @Path("/operations/consistency/run")
    @Produces(MediaType.TEXT_HTML)
    public Response runConsistencyCheck() {
        List<AccountInfo> accounts = fetchAccounts();
        return Response.ok(render(accounts, "Consistency check completed.", "",
                "operations", "worldstate", "consistency")).build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TemplateInstance render(List<AccountInfo> accounts, String message, String error,
                                     String activeTab, String ledgerSubTab, String opsSubTab) {
        String apiError = "";
        if (accounts.isEmpty() && error.isEmpty()) {
            apiError = "Cannot reach the API server — data may be incomplete.";
        }
        String effectiveError = error.isEmpty() ? apiError : error;

        NetworkStatus networkStatus = fetchNetworkStatus();
        ConsistencyReport consistency = fetchConsistency();
        BlocksResponse blocksResponse = fetchBlocks();

        long totalSupply = accounts.stream().mapToLong(a -> a.balance).sum();
        long orgCount = accounts.stream().map(a -> a.bank).distinct().count();

        Map<String, List<TransactionRecord>> txByAccount = accounts.stream()
                .collect(Collectors.toMap(a -> a.id, a -> txStore.getForAccount(a.id)));

        String closeAccountWarning = networkStatus.fabricAvailable
                ? "Deletes the account from the current world state. Its transaction history remains permanently recorded on the blockchain."
                : "This permanently removes the account from the in-memory API store. There is no blockchain record to preserve.";

        return dashboard
                .data("banks",              groupByBank(accounts))
                .data("accounts",           accounts)
                .data("transactions",       txStore.getAll())
                .data("recentTransactions", txStore.getRecent(5))
                .data("message",            message)
                .data("error",              effectiveError)
                .data("activeTab",          activeTab)
                .data("ledgerSubTab",       ledgerSubTab)
                .data("opsSubTab",          opsSubTab)
                .data("apiTests",           loadApiTestResults())
                .data("uiTests",            loadUiTestResults())
                .data("networkStatus",      networkStatus)
                .data("consistency",        consistency)
                .data("blocksResponse",     blocksResponse)
                .data("totalSupply",        totalSupply)
                .data("accountCount",       accounts.size())
                .data("orgCount",           orgCount)
                .data("txByAccount",        txByAccount)
                .data("closeAccountWarning", closeAccountWarning);
    }

    private NetworkStatus fetchNetworkStatus() {
        try {
            return apiClient.getNetworkStatus();
        } catch (Exception e) {
            NetworkStatus ns = new NetworkStatus();
            ns.fabricEnabled = false;
            ns.fabricAvailable = false;
            ns.apiHealth = "Unknown";
            return ns;
        }
    }

    private ConsistencyReport fetchConsistency() {
        try {
            return apiClient.getConsistency();
        } catch (Exception e) {
            ConsistencyReport report = new ConsistencyReport();
            report.entries = List.of();
            report.fabricAvailable = false;
            return report;
        }
    }

    private BlocksResponse fetchBlocks() {
        try {
            return apiClient.getBlocks(20);
        } catch (Exception e) {
            BlocksResponse resp = new BlocksResponse();
            resp.available = false;
            resp.blocks = List.of();
            return resp;
        }
    }

    private List<AccountInfo> fetchAccounts() {
        try {
            return apiClient.getAllAccounts();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<TestSummary> loadApiTestResults() {
        try {
            return apiClient.getApiTestResults();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<TestSummary> loadUiTestResults() {
        try (InputStream in = getClass().getResourceAsStream("/test-results.json")) {
            if (in == null) return List.of();
            return objectMapper.readValue(in, new TypeReference<List<TestSummary>>() {});
        } catch (IOException e) {
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
