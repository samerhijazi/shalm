package io.shalm.ui;

public class AccountInfo {
    public String id;
    public String owner;
    public String bank;
    public int balance;

    public String getLabel() {
        return owner + " (" + bank + ") — " + id;
    }
}
