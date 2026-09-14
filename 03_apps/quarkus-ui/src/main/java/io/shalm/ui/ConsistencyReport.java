package io.shalm.ui;

import java.util.List;

public class ConsistencyReport {
    public List<ConsistencyEntry> entries;
    public int totalChecked;
    public int mismatchCount;
    public boolean fabricAvailable;
    public String lastCheckedTimestamp;
}
