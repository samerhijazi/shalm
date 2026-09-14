package io.shalm;

public class ConsistencyEntry {
    public String accountId;
    public String owner;
    public String bank;
    public int apiBalance;
    public Integer fabricBalance;
    public Integer difference;
    public boolean match;
    public String fabricError;

    public ConsistencyEntry() {}

    public ConsistencyEntry(String accountId, String owner, String bank, int apiBalance,
                             Integer fabricBalance, Integer difference, boolean match, String fabricError) {
        this.accountId = accountId;
        this.owner = owner;
        this.bank = bank;
        this.apiBalance = apiBalance;
        this.fabricBalance = fabricBalance;
        this.difference = difference;
        this.match = match;
        this.fabricError = fabricError;
    }
}
