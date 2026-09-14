package io.shalm.ui;

public class TransferResponse {
    public String transactionId;
    public String status;
    public String message;

    public String fabricTransactionId;
    public Long fabricBlockNumber;
    public String fabricValidationCode;
    public String fabricStatus;
}
