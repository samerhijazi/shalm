package io.shalm;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/fabric/assets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OwnershipResource {

    private static final Logger LOG = Logger.getLogger(OwnershipResource.class);

    @Inject
    FabricGatewayService fabric;

    @GET
    public Response list() {
        if (!fabric.isAvailable()) {
            return unavailable();
        }
        try {
            return Response.ok(fabric.listAssets()).build();
        } catch (Exception e) {
            LOG.warnf("Fabric asset query failed: %s", e.getMessage());
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(Map.of("status", "failed", "error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String assetId) {
        if (!fabric.isAvailable()) {
            return unavailable();
        }
        try {
            return Response.ok(fabric.getAsset(assetId)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("status", "failed", "error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/{id}/transfer")
    public Response transfer(@PathParam("id") String assetId, OwnershipTransferRequest request) {
        if (!fabric.isOwnershipTransferAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("status", "down",
                            "error", "Both Org1MSP and Org2MSP gateway identities are required"))
                    .build();
        }
        if (request == null || request.newOwner == null || request.newOwner.isBlank()
                || request.newOwnerMsp == null
                || !("Org1MSP".equals(request.newOwnerMsp) || "Org2MSP".equals(request.newOwnerMsp))) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("status", "invalid",
                            "error", "newOwner and newOwnerMsp (Org1MSP or Org2MSP) are required"))
                    .build();
        }
        try {
            OwnershipTransferResponse response = fabric.transferAsset(
                    assetId, request.newOwner.trim(), request.newOwnerMsp);
            LOG.infof("asset-ownership-transfer-success asset=%s from=%s to=%s tx=%s",
                    assetId, response.submittedByMsp, request.newOwnerMsp, response.transactionId);
            return Response.ok(response).build();
        } catch (Exception e) {
            LOG.warnf("Asset ownership transfer failed for %s: %s", assetId, e.getMessage());
            return Response.status(422)
                    .entity(Map.of("status", "failed", "error", e.getMessage()))
                    .build();
        }
    }

    private static Response unavailable() {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("error", "Fabric ledger not available", "status", "down"))
                .build();
    }
}
