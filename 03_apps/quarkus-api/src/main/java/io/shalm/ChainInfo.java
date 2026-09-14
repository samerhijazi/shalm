package io.shalm;

public class ChainInfo {
    public long height;
    public String currentBlockHash;
    public String previousBlockHash;

    public ChainInfo() {}

    public ChainInfo(long height, String currentBlockHash, String previousBlockHash) {
        this.height = height;
        this.currentBlockHash = currentBlockHash;
        this.previousBlockHash = previousBlockHash;
    }
}
