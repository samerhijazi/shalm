package io.shalm;

import java.util.List;

public class BlocksResponse {
    public boolean available;
    public Long latestBlockNumber;
    public List<BlockDetail> blocks;

    public BlocksResponse() {}

    public BlocksResponse(boolean available, Long latestBlockNumber, List<BlockDetail> blocks) {
        this.available = available;
        this.latestBlockNumber = latestBlockNumber;
        this.blocks = blocks;
    }
}
