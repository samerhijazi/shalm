package io.shalm;

public class BlockTransaction {
    public String txId;
    public String type;
    public String validationCode;
    public String timestamp;

    public BlockTransaction() {}

    public BlockTransaction(String txId, String type, String validationCode, String timestamp) {
        this.txId = txId;
        this.type = type;
        this.validationCode = validationCode;
        this.timestamp = timestamp;
    }
}
