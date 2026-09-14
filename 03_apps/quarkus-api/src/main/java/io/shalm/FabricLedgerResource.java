package io.shalm;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@Path("/fabric")
@Produces(MediaType.APPLICATION_JSON)
public class FabricLedgerResource {

    private static final Logger LOG = Logger.getLogger(FabricLedgerResource.class);

    @Inject
    FabricGatewayService fabric;

    @Inject
    FabricLedgerService ledger;

    @GET
    @Path("/blocks")
    public Response getBlocks(@QueryParam("limit") @DefaultValue("20") int limit) {
        if (!fabric.isAvailable()) {
            return unavailable();
        }
        try {
            List<BlockDetail> blocks = ledger.getRecentBlocks(limit);
            Long latest = blocks.isEmpty() ? null : blocks.get(0).number;
            return Response.ok(new BlocksResponse(true, latest, blocks)).build();
        } catch (Exception e) {
            LOG.warnf("Fabric getRecentBlocks failed: %s", e.getMessage());
            return Response.status(503)
                    .entity(Map.of("error", e.getMessage(), "status", "down"))
                    .build();
        }
    }

    @GET
    @Path("/blocks/{number}")
    public Response getBlock(@PathParam("number") long number) {
        if (!fabric.isAvailable()) {
            return unavailable();
        }
        if (number < 0) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Block not found: " + number))
                    .build();
        }
        try {
            BlockDetail detail = ledger.getBlock(number);
            return Response.ok(detail).build();
        } catch (Exception e) {
            LOG.warnf("Fabric getBlock(%d) failed: %s", number, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Block not found: " + number))
                    .build();
        }
    }

    private static Response unavailable() {
        return Response.status(503)
                .entity(Map.of("error", "Fabric ledger not available", "status", "down"))
                .build();
    }
}
