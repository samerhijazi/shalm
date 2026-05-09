package io.shalm.ui;

public class TransferRequest {
    public String from;
    public String to;
    public int amount;

    public TransferRequest() {}

    public TransferRequest(String from, String to, int amount) {
        this.from = from;
        this.to = to;
        this.amount = amount;
    }
}
