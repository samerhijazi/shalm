package io.shalm.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TransactionRecord {
    public String txId;
    public String from;
    public String to;
    public int amount;
    public String status;
    public String timestamp;

    public TransactionRecord(String txId, String from, String to, int amount, String status) {
        this.txId = txId;
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.status = status;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
