package io.shalm.ui;

public class CreateAccountRequest {
    public String id;
    public String owner;
    public String bank;
    public int    initialBalance;

    public CreateAccountRequest(String id, String owner, String bank, int initialBalance) {
        this.id             = id;
        this.owner          = owner;
        this.bank           = bank;
        this.initialBalance = initialBalance;
    }
}
