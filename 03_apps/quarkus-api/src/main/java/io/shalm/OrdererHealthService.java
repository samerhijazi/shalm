package io.shalm;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Checks the orderer's real health via Fabric's Operations service
 * (GET /healthz) rather than the main orderer gRPC port, which only serves
 * Fabric's own AtomicBroadcast/Cluster services — not a generic health check.
 */
@ApplicationScoped
public class OrdererHealthService {

    private static final Logger LOG = Logger.getLogger(OrdererHealthService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @ConfigProperty(name = "fabric.orderer.host")
    String ordererHost;

    @ConfigProperty(name = "fabric.orderer.operations.port")
    int operationsPort;

    public boolean isHealthy() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + ordererHost + ":" + operationsPort + "/healthz"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            LOG.debugf("Orderer health check failed: %s", e.getMessage());
            return false;
        }
    }
}
