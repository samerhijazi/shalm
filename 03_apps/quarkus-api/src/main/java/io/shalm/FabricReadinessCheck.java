package io.shalm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class FabricReadinessCheck implements HealthCheck {

    @Inject
    FabricGatewayService fabric;

    @Override
    public HealthCheckResponse call() {
        if (!fabric.isEnabled()) {
            return HealthCheckResponse.named("fabric-ledger")
                    .up()
                    .withData("enabled", false)
                    .build();
        }
        try {
            int balance = fabric.checkHealth();
            return HealthCheckResponse.named("fabric-ledger")
                    .up()
                    .withData("probeBalance", balance)
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.named("fabric-ledger")
                    .down()
                    .withData("error", safeMessage(e))
                    .build();
        }
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
