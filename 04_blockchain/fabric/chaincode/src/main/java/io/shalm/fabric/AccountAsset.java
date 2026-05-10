package io.shalm.fabric;

public class AccountAsset {

    private String id;
    private String owner;
    private String bankId;
    private int    balance;

    public AccountAsset() {}

    public AccountAsset(String id, String owner, String bankId, int balance) {
        this.id      = id;
        this.owner   = owner;
        this.bankId  = bankId;
        this.balance = balance;
    }

    public String getId()      { return id; }
    public String getOwner()   { return owner; }
    public String getBankId()  { return bankId; }
    public int    getBalance() { return balance; }

    public void setId(String id)          { this.id = id; }
    public void setOwner(String owner)    { this.owner = owner; }
    public void setBankId(String bankId)  { this.bankId = bankId; }
    public void setBalance(int balance)   { this.balance = balance; }
}
