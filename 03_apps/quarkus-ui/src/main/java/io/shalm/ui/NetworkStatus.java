package io.shalm.ui;

public class NetworkStatus {
    public String channel;
    public String chaincodeName;
    public String chaincodeVersion;
    public String mspId;
    public String peerHost;
    public int peerPort;
    public String ordererHost;
    public int ordererPort;
    public boolean ordererAvailable;
    public boolean fabricEnabled;
    public boolean fabricAvailable;
    public Long latestBlockNumber;
    public String apiHealth;
    public String lastCheckedTimestamp;

    public String statusBadge() {
        if (!fabricEnabled) {
            return "Not configured";
        }
        return fabricAvailable ? "Healthy" : "Unavailable";
    }
}
