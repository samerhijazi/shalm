package io.shalm;

public class TransferResponse {
    public String transactionId;
    public String status;
    public String message;

    // Populated only when the in-memory transfer succeeded and a Fabric-backed dual-write
    // was attempted; "fabricStatus" is "committed" | "failed" | "unavailable".
    public String fabricTransactionId;
    public Long fabricBlockNumber;
    public String fabricValidationCode;
    public String fabricStatus;

    public TransferResponse(String transactionId, String status, String message) {
        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
    }

    public void applyFabricResult(FabricTransferResult result) {
        this.fabricTransactionId = result.transactionId;
        this.fabricBlockNumber = result.blockNumber;
        this.fabricValidationCode = result.validationCode;
        this.fabricStatus = "committed";
    }

    public void markFabricUnavailable() {
        this.fabricStatus = "unavailable";
    }

    public void markFabricFailed() {
        this.fabricStatus = "failed";
    }
}
