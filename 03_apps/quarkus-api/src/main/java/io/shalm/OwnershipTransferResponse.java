package io.shalm;

public class OwnershipTransferResponse {
    public OwnedAsset asset;
    public String transactionId;
    public long blockNumber;
    public String validationCode;
    public String status;
    public String submittedByMsp;

    public OwnershipTransferResponse() {}

    public OwnershipTransferResponse(OwnedAsset asset, String transactionId, long blockNumber,
                                     String validationCode, String status, String submittedByMsp) {
        this.asset = asset;
        this.transactionId = transactionId;
        this.blockNumber = blockNumber;
        this.validationCode = validationCode;
        this.status = status;
        this.submittedByMsp = submittedByMsp;
    }
}
