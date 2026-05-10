package io.shalm;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.Map;
import java.util.UUID;

@Path("/fabric")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FabricResource {

    private static final Logger LOG = Logger.getLogger(FabricResource.class);

    @Inject
    FabricGatewayService fabric;

    @GET
    @Path("/balance/{id}")
    public Response balance(@PathParam("id") String accountId) {
        if (!fabric.isAvailable()) {
            return unavailable();
        }
        try {
            int bal = fabric.queryBalance(accountId);
            return Response.ok(Map.of("accountId", accountId, "balance", bal)).build();
        } catch (Exception e) {
            LOG.warnf("Fabric queryBalance failed for %s: %s", accountId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/transfer")
    public Response transfer(FabricTransferRequest req) {
        if (!fabric.isAvailable()) {
            return unavailable();
        }
        if (req == null || req.from == null || req.to == null || req.amount <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Bad request: from, to, and amount > 0 required"))
                    .build();
        }
        String txId = UUID.randomUUID().toString();
        try {
            fabric.transfer(req.from, req.to, req.amount);
            MDC.put("tx_id", txId);
            MDC.put("from", req.from);
            MDC.put("to", req.to);
            MDC.put("amount", req.amount);
            MDC.put("ledger", "fabric");
            LOG.info("fabric-transfer-success");
            MDC.clear();
            return Response.ok(Map.of("txId", txId, "status", "success")).build();
        } catch (Exception e) {
            LOG.warnf("Fabric transfer failed [%s -> %s %d]: %s", req.from, req.to, req.amount, e.getMessage());
            return Response.status(422)
                    .entity(Map.of("txId", txId, "status", "failed", "error", e.getMessage()))
                    .build();
        }
    }

    private static Response unavailable() {
        return Response.status(503)
                .entity(Map.of("error", "Fabric ledger not available — deploy Phase 4 and set FABRIC_ENABLED=true"))
                .build();
    }
}
