package io.shalm.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.metrics.Metrics;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ResponseUtils;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.hyperledger.fabric.traces.Traces;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

public class BankChaincode extends ChaincodeBase {

    private static final String ASSET_PREFIX = "asset:";
    private static final String ORG1_MSP = "Org1MSP";
    private static final String ORG2_MSP = "Org2MSP";

    private final ObjectMapper mapper = new ObjectMapper();

    BankChaincode() {
        // Unit tests exercise transaction behavior without starting the CCAAS server.
    }

    public BankChaincode(String[] args) {
        // We construct NettyChaincodeServer manually instead of calling the inherited
        // start(args) (start() always runs in *client* mode -- it dials out to a peer --
        // there's no way to make it listen for CCAAS). That means every step start(args)
        // normally does before actually connecting must be replicated here, in the same
        // order, or something inside ChaincodeBase's request-handling internals ends up
        // reading state that was never initialized:
        //   initializeLogging, processEnvironmentOptions, processCommandLineOptions,
        //   validateOptions, getChaincodeConfig, Metrics.initialize, Traces.initialize
        // Skipping getChaincodeConfig() throws "Chaincode config not available" on the
        // first real invocation; skipping Traces.initialize() throws "No provider set"
        // from Traces.getProvider() at the same point, one step later in the same call.
        initializeLogging();
        processEnvironmentOptions();
        processCommandLineOptions(args);
        validateOptions();
        Properties props = getChaincodeConfig();
        Metrics.initialize(props);
        Traces.initialize(props);
    }

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

                case "ReadAsset":
                    requireArgs(p, 1, "ReadAsset(assetId)");
                    return payload(requireAssetRaw(stub, p.get(0)));

                case "GetAllAssets":
                    return payload(getAllAssets(stub));

                case "TransferAsset":
                    requireArgs(p, 3, "TransferAsset(assetId, newOwner, newOwnerMsp)");
                    OwnedAsset transferred = transferAsset(stub, p.get(0), p.get(1), p.get(2));
                    return payload(mapper.writeValueAsString(transferred));

                case "QueryBalance":
                    requireArgs(p, 1, "QueryBalance(accountId)");
                    int bal = queryBalance(stub, p.get(0));
                    return payload(String.valueOf(bal));

                case "getAccount":
                    requireArgs(p, 1, "getAccount(accountId)");
                    return payload(requireRaw(stub, p.get(0)));

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
        seedAssetIfAbsent(stub, "ASSET-001", "Trade document Alpha",
                "Bank1 Treasury", ORG1_MSP, 25000);
        seedAssetIfAbsent(stub, "ASSET-002", "Trade document Beta",
                "Bank2 Treasury", ORG2_MSP, 40000);
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
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }
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

    private String getAllAssets(ChaincodeStub stub) throws Exception {
        List<OwnedAsset> assets = new ArrayList<>();
        try (QueryResultsIterator<KeyValue> results =
                     stub.getStateByRange(ASSET_PREFIX, ASSET_PREFIX + "\uffff")) {
            for (KeyValue result : results) {
                assets.add(mapper.readValue(result.getStringValue(), OwnedAsset.class));
            }
        }
        assets.sort(Comparator.comparing(OwnedAsset::getId));
        return mapper.writeValueAsString(assets);
    }

    private OwnedAsset transferAsset(ChaincodeStub stub, String assetId, String newOwner,
                                     String newOwnerMsp) throws Exception {
        if (newOwner == null || newOwner.isBlank()) {
            throw new IllegalArgumentException("New owner is required");
        }
        requireKnownMsp(newOwnerMsp);

        String key = assetKey(assetId);
        OwnedAsset asset = mapper.readValue(requireAssetRaw(stub, assetId), OwnedAsset.class);
        String invokerMsp = stub.getMspId();
        if (!asset.getOwnerMsp().equals(invokerMsp)) {
            throw new SecurityException("Only current owner MSP " + asset.getOwnerMsp()
                    + " may transfer asset " + assetId + "; invoker is " + invokerMsp);
        }
        if (asset.getOwnerMsp().equals(newOwnerMsp)) {
            throw new IllegalArgumentException("Target MSP must differ from current owner MSP");
        }

        asset.setOwner(newOwner.trim());
        asset.setOwnerMsp(newOwnerMsp);
        asset.setLastTransferTxId(stub.getTxId());
        String json = mapper.writeValueAsString(asset);
        stub.putStringState(key, json);
        stub.setEvent("AssetTransferred", json.getBytes(StandardCharsets.UTF_8));
        return asset;
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

    private void seedAssetIfAbsent(ChaincodeStub stub, String id, String description,
                                   String owner, String ownerMsp, int value) throws Exception {
        String key = assetKey(id);
        if (!exists(stub, key)) {
            OwnedAsset asset = new OwnedAsset(id, description, owner, ownerMsp, value, "");
            stub.putStringState(key, mapper.writeValueAsString(asset));
        }
    }

    private static String assetKey(String assetId) {
        if (assetId == null || assetId.isBlank()) {
            throw new IllegalArgumentException("Asset ID is required");
        }
        return ASSET_PREFIX + assetId.trim();
    }

    private static void requireKnownMsp(String mspId) {
        if (!ORG1_MSP.equals(mspId) && !ORG2_MSP.equals(mspId)) {
            throw new IllegalArgumentException("Target MSP must be Org1MSP or Org2MSP");
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

    private String requireAssetRaw(ChaincodeStub stub, String assetId) {
        String raw = stub.getStringState(assetKey(assetId));
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Asset not found: " + assetId);
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

    private static Response payload(String value) {
        return ResponseUtils.newSuccessResponse(value.getBytes(StandardCharsets.UTF_8));
    }
}
