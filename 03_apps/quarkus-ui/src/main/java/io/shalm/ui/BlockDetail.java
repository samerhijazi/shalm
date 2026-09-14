package io.shalm.ui;

import java.util.List;

public class BlockDetail {
    public long number;
    public int txCount;
    public String dataHash;
    public String previousHash;
    public String timestamp;
    public String channel;
    public List<BlockTransaction> transactions;

    public String shortDataHash() {
        return shorten(dataHash);
    }

    public String shortPreviousHash() {
        return shorten(previousHash);
    }

    private static String shorten(String hash) {
        if (hash == null || hash.length() <= 12) {
            return hash;
        }
        return hash.substring(0, 6) + "…" + hash.substring(hash.length() - 6);
    }
}
