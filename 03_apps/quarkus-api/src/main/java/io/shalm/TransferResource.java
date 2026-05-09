package io.shalm;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.UUID;

@Path("/transfer")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TransferResource {

    private static final Logger LOG = Logger.getLogger(TransferResource.class);

    @Inject
    AccountService accountService;

    @Inject
    MeterRegistry registry;

    private Counter requestCount;
    private Counter errorCount;
    private Timer requestLatency;

    @PostConstruct
    void init() {
        requestCount = registry.counter("transfer_request_count");
        errorCount = registry.counter("transfer_error_count");
        requestLatency = registry.timer("transfer_request_latency");
    }

    @POST
    public Response transfer(TransferRequest req) {
        requestCount.increment();
        String txId = UUID.randomUUID().toString();
        Timer.Sample sample = Timer.start(registry);

        try {
            if (req == null || req.from == null || req.to == null || req.amount <= 0) {
                errorCount.increment();
                logTransfer(txId, req == null ? null : req.from, req == null ? null : req.to,
                        req == null ? 0 : req.amount, "invalid");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new TransferResponse(txId, "invalid", "Bad request parameters"))
                        .build();
            }

            boolean ok = accountService.transfer(req.from, req.to, req.amount);
            if (ok) {
                logTransfer(txId, req.from, req.to, req.amount, "success");
                return Response.ok(new TransferResponse(txId, "success", "Transfer completed")).build();
            } else {
                errorCount.increment();
                logTransfer(txId, req.from, req.to, req.amount, "failed");
                return Response.status(422)
                        .entity(new TransferResponse(txId, "failed", "Insufficient funds or unknown account"))
                        .build();
            }
        } finally {
            sample.stop(requestLatency);
        }
    }

    private void logTransfer(String txId, String from, String to, int amount, String status) {
        MDC.put("transaction_id", txId);
        MDC.put("from", from);
        MDC.put("to", to);
        MDC.put("amount", amount);
        MDC.put("status", status);
        LOG.info("transfer");
        MDC.clear();
    }
}
