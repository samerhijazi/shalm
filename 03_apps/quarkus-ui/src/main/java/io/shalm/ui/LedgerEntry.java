package io.shalm.ui;

public class LedgerEntry {
    public String id;
    public String owner;
    public String bank;
    public int apiBalance;
    public String fabricBalance; // numeric string, or "N/A" when Fabric is unavailable

    public LedgerEntry(AccountInfo acc, String fabricBalance) {
        this.id = acc.id;
        this.owner = acc.owner;
        this.bank = acc.bank;
        this.apiBalance = acc.balance;
        this.fabricBalance = fabricBalance;
    }
}
