package io.shalm;

import java.time.Instant;

public class Account {
    public String id;
    public String owner;
    public String bank;
    public int balance;
    public Instant updatedAt;

    public Account() {}

    public Account(String id, String owner, String bank, int balance) {
        this.id = id;
        this.owner = owner;
        this.bank = bank;
        this.balance = balance;
        this.updatedAt = Instant.now();
    }
}
