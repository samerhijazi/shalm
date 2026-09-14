package io.shalm;

public class FabricTransferResult {
    public String transactionId;
    public long blockNumber;
    public String validationCode;
    public boolean successful;

    public FabricTransferResult() {}

    public FabricTransferResult(String transactionId, long blockNumber, String validationCode, boolean successful) {
        this.transactionId = transactionId;
        this.blockNumber = blockNumber;
        this.validationCode = validationCode;
        this.successful = successful;
    }
}
