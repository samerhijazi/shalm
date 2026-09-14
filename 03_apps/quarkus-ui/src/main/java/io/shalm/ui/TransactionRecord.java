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

    // Fabric metadata — populated only when the dual-write to Fabric actually ran and
    // succeeded (see TransferResponse.fabricStatus == "committed"). Null/blank otherwise;
    // templates must guard on these before rendering.
    public Long blockNumber;
    public String validationCode;
    public String channel;
    public String chaincodeName;
    public String function;

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

    public void applyFabricMetadata(Long blockNumber, String validationCode, String channel, String chaincodeName) {
        this.blockNumber = blockNumber;
        this.validationCode = validationCode;
        this.channel = channel;
        this.chaincodeName = chaincodeName;
        this.function = "Transfer";
    }
}
