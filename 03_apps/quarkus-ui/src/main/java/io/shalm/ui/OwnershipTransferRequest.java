package io.shalm.ui;

public class OwnershipTransferRequest {
    public String newOwner;
    public String newOwnerMsp;

    public OwnershipTransferRequest() {}

    public OwnershipTransferRequest(String newOwner, String newOwnerMsp) {
        this.newOwner = newOwner;
        this.newOwnerMsp = newOwnerMsp;
    }
}
