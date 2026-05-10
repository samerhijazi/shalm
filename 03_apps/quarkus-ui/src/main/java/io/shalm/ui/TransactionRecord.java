package io.shalm.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TransactionRecord {
    public String txId;
    public String fromId;
    public String fromLabel;
    public String toId;
    public String toLabel;
    public int amount;
    public String status;
    public String timestamp;

    public TransactionRecord(String txId,
                             String fromId, String fromLabel,
                             String toId,   String toLabel,
                             int amount, String status) {
        this.txId      = txId;
        this.fromId    = fromId;
        this.fromLabel = fromLabel;
        this.toId      = toId;
        this.toLabel   = toLabel;
        this.amount    = amount;
        this.status    = status;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
