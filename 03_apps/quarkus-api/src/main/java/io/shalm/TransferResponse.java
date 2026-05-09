package io.shalm;

public class TransferResponse {
    public String transactionId;
    public String status;
    public String message;

    public TransferResponse(String transactionId, String status, String message) {
        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
    }
}
