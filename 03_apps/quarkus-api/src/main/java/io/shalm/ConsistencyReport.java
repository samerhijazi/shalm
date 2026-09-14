package io.shalm;

import java.util.List;

public class ConsistencyReport {
    public List<ConsistencyEntry> entries;
    public int totalChecked;
    public int mismatchCount;
    public boolean fabricAvailable;
    public String lastCheckedTimestamp;

    public ConsistencyReport() {}

    public ConsistencyReport(List<ConsistencyEntry> entries, int totalChecked, int mismatchCount,
                              boolean fabricAvailable, String lastCheckedTimestamp) {
        this.entries = entries;
        this.totalChecked = totalChecked;
        this.mismatchCount = mismatchCount;
        this.fabricAvailable = fabricAvailable;
        this.lastCheckedTimestamp = lastCheckedTimestamp;
    }
}
