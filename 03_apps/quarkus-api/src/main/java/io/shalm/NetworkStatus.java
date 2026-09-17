package io.shalm;

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

    public NetworkStatus() {}

    public NetworkStatus(String channel, String chaincodeName, String chaincodeVersion, String mspId,
                          String peerHost, int peerPort, String ordererHost, int ordererPort,
                          boolean ordererAvailable, boolean fabricEnabled, boolean fabricAvailable,
                          Long latestBlockNumber, String apiHealth, String lastCheckedTimestamp) {
        this.channel = channel;
        this.chaincodeName = chaincodeName;
        this.chaincodeVersion = chaincodeVersion;
        this.mspId = mspId;
        this.peerHost = peerHost;
        this.peerPort = peerPort;
        this.ordererHost = ordererHost;
        this.ordererPort = ordererPort;
        this.ordererAvailable = ordererAvailable;
        this.fabricEnabled = fabricEnabled;
        this.fabricAvailable = fabricAvailable;
        this.latestBlockNumber = latestBlockNumber;
        this.apiHealth = apiHealth;
        this.lastCheckedTimestamp = lastCheckedTimestamp;
    }
}
