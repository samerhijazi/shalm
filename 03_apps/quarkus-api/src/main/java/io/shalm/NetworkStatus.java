package io.shalm;

public class NetworkStatus {
    public String channel;
    public String chaincodeName;
    public String chaincodeVersion;
    public String mspId;
    public String peerHost;
    public int peerPort;
    public boolean fabricEnabled;
    public boolean fabricAvailable;
    public Long latestBlockNumber;
    public String apiHealth;
    public String lastCheckedTimestamp;

    public NetworkStatus() {}

    public NetworkStatus(String channel, String chaincodeName, String chaincodeVersion, String mspId,
                          String peerHost, int peerPort, boolean fabricEnabled, boolean fabricAvailable,
                          Long latestBlockNumber, String apiHealth, String lastCheckedTimestamp) {
        this.channel = channel;
        this.chaincodeName = chaincodeName;
        this.chaincodeVersion = chaincodeVersion;
        this.mspId = mspId;
        this.peerHost = peerHost;
        this.peerPort = peerPort;
        this.fabricEnabled = fabricEnabled;
        this.fabricAvailable = fabricAvailable;
        this.latestBlockNumber = latestBlockNumber;
        this.apiHealth = apiHealth;
        this.lastCheckedTimestamp = lastCheckedTimestamp;
    }
}
