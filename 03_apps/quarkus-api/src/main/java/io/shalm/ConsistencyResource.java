package io.shalm;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Moves the per-account API-vs-Fabric balance comparison out of quarkus-ui (where it used
 * to live inline in DashboardResource.buildLedger()) so it's a single, reusable source of
 * truth for both the Dashboard's consistency card and Operations -> Consistency.
 * Always returns 200 — an unreachable Fabric ledger is a reportable state, not an error.
 */
@Path("/consistency")
@Produces(MediaType.APPLICATION_JSON)
public class ConsistencyResource {

    @Inject
    AccountService accountService;

    @Inject
    FabricGatewayService fabric;

    @GET
    public ConsistencyReport getConsistency() {
        boolean fabricAvailable = fabric.isAvailable();
        Collection<Account> accounts = accountService.getAllAccounts();
        List<ConsistencyEntry> entries = new ArrayList<>();
        int mismatches = 0;

        for (Account acc : accounts) {
            Integer fabricBalance = null;
            Integer difference = null;
            boolean match = false;
            String fabricError = null;

            if (fabricAvailable) {
                try {
                    fabricBalance = fabric.queryBalance(acc.id);
                    difference = fabricBalance - acc.balance;
                    match = difference == 0;
                } catch (Exception e) {
                    fabricError = e.getMessage();
                }
            } else {
                fabricError = "Fabric ledger not available";
            }

            if (fabricBalance != null && !match) {
                mismatches++;
            }
            entries.add(new ConsistencyEntry(acc.id, acc.owner, acc.bank, acc.balance,
                    fabricBalance, difference, match, fabricError));
        }

        return new ConsistencyReport(entries, entries.size(), mismatches, fabricAvailable, Instant.now().toString());
    }
}
