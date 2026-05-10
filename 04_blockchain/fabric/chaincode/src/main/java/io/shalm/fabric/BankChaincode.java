package io.shalm.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ResponseUtils;

import java.util.List;

public class BankChaincode extends ChaincodeBase {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Response init(ChaincodeStub stub) {
        try {
            String fn = stub.getFunction();
            if ("InitLedger".equals(fn) || "init".equals(fn)) {
                initLedger(stub);
            }
            return ResponseUtils.newSuccessResponse("Initialized");
        } catch (Exception e) {
            return ResponseUtils.newErrorResponse(e.getMessage());
        }
    }

    @Override
    public Response invoke(ChaincodeStub stub) {
        try {
            String fn = stub.getFunction();
            List<String> p = stub.getParameters();
            switch (fn) {
                case "InitLedger":
                    initLedger(stub);
                    return ResponseUtils.newSuccessResponse("Ledger initialized");

                case "Transfer":
                    requireArgs(p, 3, "Transfer(from, to, amount)");
                    transfer(stub, p.get(0), p.get(1), Integer.parseInt(p.get(2)));
                    return ResponseUtils.newSuccessResponse("Transfer complete");

                case "QueryBalance":
                    requireArgs(p, 1, "QueryBalance(accountId)");
                    int bal = queryBalance(stub, p.get(0));
                    return ResponseUtils.newSuccessResponse(String.valueOf(bal));

                case "getAccount":
                    requireArgs(p, 1, "getAccount(accountId)");
                    return ResponseUtils.newSuccessResponse(requireRaw(stub, p.get(0)));

                case "createAccount":
                    requireArgs(p, 4, "createAccount(id, bankId, owner, initialBalance)");
                    createAccount(stub, p.get(0), p.get(1), p.get(2), Integer.parseInt(p.get(3)));
                    return ResponseUtils.newSuccessResponse("Account created");

                case "deleteAccount":
                    requireArgs(p, 1, "deleteAccount(accountId)");
                    deleteAccount(stub, p.get(0));
                    return ResponseUtils.newSuccessResponse("Account deleted");

                case "deposit":
                    requireArgs(p, 2, "deposit(accountId, amount)");
                    deposit(stub, p.get(0), Integer.parseInt(p.get(1)));
                    return ResponseUtils.newSuccessResponse("Deposited");

                default:
                    return ResponseUtils.newErrorResponse("Unknown function: " + fn);
            }
        } catch (Exception e) {
            return ResponseUtils.newErrorResponse(e.getMessage());
        }
    }

    // ── business logic ────────────────────────────────────────────────────────

    private void initLedger(ChaincodeStub stub) throws Exception {
        seedIfAbsent(stub, "ACC-B1-001", "Bank1", "ClientA", 1000);
        seedIfAbsent(stub, "ACC-B1-002", "Bank1", "ClientC",  500);
        seedIfAbsent(stub, "ACC-B2-001", "Bank2", "ClientB", 1000);
        seedIfAbsent(stub, "ACC-B2-002", "Bank2", "ClientD",  500);
    }

    private void createAccount(ChaincodeStub stub, String id, String bankId,
                               String owner, int initialBalance) throws Exception {
        if (exists(stub, id)) {
            throw new IllegalArgumentException("Account already exists: " + id);
        }
        put(stub, new AccountAsset(id, owner, bankId, initialBalance));
    }

    private int queryBalance(ChaincodeStub stub, String id) throws Exception {
        return read(stub, id).getBalance();
    }

    private void transfer(ChaincodeStub stub, String fromId, String toId,
                          int amount) throws Exception {
        AccountAsset from = read(stub, fromId);
        AccountAsset to   = read(stub, toId);
        if (from.getBalance() < amount) {
            throw new IllegalStateException("Insufficient funds in " + fromId);
        }
        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);
        put(stub, from);
        put(stub, to);
    }

    private void deposit(ChaincodeStub stub, String id, int amount) throws Exception {
        AccountAsset account = read(stub, id);
        account.setBalance(account.getBalance() + amount);
        put(stub, account);
    }

    private void deleteAccount(ChaincodeStub stub, String id) throws Exception {
        requireRaw(stub, id);
        stub.delState(id);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void seedIfAbsent(ChaincodeStub stub, String id, String bank,
                               String owner, int balance) throws Exception {
        if (!exists(stub, id)) {
            put(stub, new AccountAsset(id, owner, bank, balance));
        }
    }

    private boolean exists(ChaincodeStub stub, String id) {
        String raw = stub.getStringState(id);
        return raw != null && !raw.isEmpty();
    }

    private String requireRaw(ChaincodeStub stub, String id) {
        String raw = stub.getStringState(id);
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Account not found: " + id);
        }
        return raw;
    }

    private AccountAsset read(ChaincodeStub stub, String id) throws Exception {
        return mapper.readValue(requireRaw(stub, id), AccountAsset.class);
    }

    private void put(ChaincodeStub stub, AccountAsset account) throws Exception {
        stub.putStringState(account.getId(), mapper.writeValueAsString(account));
    }

    private static void requireArgs(List<String> args, int n, String usage) {
        if (args.size() < n) {
            throw new IllegalArgumentException("Expected " + n + " args: " + usage);
        }
    }
}
