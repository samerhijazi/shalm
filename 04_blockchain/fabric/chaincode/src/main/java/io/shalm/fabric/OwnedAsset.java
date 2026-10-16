package io.shalm.fabric;

public class OwnedAsset {

    private String id;
    private String description;
    private String owner;
    private String ownerMsp;
    private int value;
    private String lastTransferTxId;

    public OwnedAsset() {}

    public OwnedAsset(String id, String description, String owner, String ownerMsp,
                      int value, String lastTransferTxId) {
        this.id = id;
        this.description = description;
        this.owner = owner;
        this.ownerMsp = ownerMsp;
        this.value = value;
        this.lastTransferTxId = lastTransferTxId;
    }

    public String getId()               { return id; }
    public String getDescription()      { return description; }
    public String getOwner()            { return owner; }
    public String getOwnerMsp()         { return ownerMsp; }
    public int getValue()               { return value; }
    public String getLastTransferTxId() { return lastTransferTxId; }

    public void setId(String id)                             { this.id = id; }
    public void setDescription(String description)           { this.description = description; }
    public void setOwner(String owner)                       { this.owner = owner; }
    public void setOwnerMsp(String ownerMsp)                 { this.ownerMsp = ownerMsp; }
    public void setValue(int value)                          { this.value = value; }
    public void setLastTransferTxId(String lastTransferTxId) { this.lastTransferTxId = lastTransferTxId; }
}
