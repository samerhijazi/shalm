package io.shalm;

import java.util.List;

public class BlockDetail {
    public long number;
    public int txCount;
    public String dataHash;
    public String previousHash;
    public String timestamp;
    public String channel;
    public List<BlockTransaction> transactions;

    public BlockDetail() {}

    public BlockDetail(long number, int txCount, String dataHash, String previousHash,
                        String timestamp, String channel, List<BlockTransaction> transactions) {
        this.number = number;
        this.txCount = txCount;
        this.dataHash = dataHash;
        this.previousHash = previousHash;
        this.timestamp = timestamp;
        this.channel = channel;
        this.transactions = transactions;
    }
}
