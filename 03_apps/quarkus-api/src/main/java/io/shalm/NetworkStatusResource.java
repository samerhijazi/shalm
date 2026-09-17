package io.shalm;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Always returns 200 — even when Fabric is down — so the Operations/Dashboard pages can
 * render Healthy/Degraded/Unavailable/Unknown badges without treating a down ledger as an
 * HTTP error.
 */
@Path("/fabric/network-status")
@Produces(MediaType.APPLICATION_JSON)
public class NetworkStatusResource {

    private static final Logger LOG = Logger.getLogger(NetworkStatusResource.class);

    @Inject
    FabricGatewayService fabric;

    @Inject
    FabricLedgerService ledger;

    @Inject
    OrdererHealthService ordererHealth;

    @ConfigProperty(name = "fabric.orderer.host")
    String ordererHost;

    @ConfigProperty(name = "fabric.orderer.port")
    int ordererPort;

    @GET
    public NetworkStatus get() {
        boolean available = fabric.isAvailable();
        Long latestBlock = null;

        if (available) {
            try {
                ChainInfo chainInfo = ledger.getChainInfo();
                latestBlock = chainInfo.height - 1;
            } catch (Exception e) {
                LOG.warnf("Fabric network-status chain info lookup failed: %s", e.getMessage());
                available = false;
            }
        }

        boolean ordererAvailable = fabric.isEnabled() && ordererHealth.isHealthy();

        return new NetworkStatus(
                fabric.getChannelName(),
                fabric.getChaincodeName(),
                fabric.getChaincodeVersion(),
                fabric.getMspId(),
                fabric.getPeerHost(),
                fabric.getPeerPort(),
                ordererHost,
                ordererPort,
                ordererAvailable,
                fabric.isEnabled(),
                available,
                latestBlock,
                "Healthy",
                Instant.now().toString());
    }
}
