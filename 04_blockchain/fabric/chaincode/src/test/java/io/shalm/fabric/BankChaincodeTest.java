package io.shalm.fabric;

import org.hyperledger.fabric.shim.Chaincode;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankChaincodeTest {

    private final BankChaincode chaincode = new BankChaincode();

    @Test
    void queryBalanceReturnsTheBalanceInTheGatewayPayload() {
        ChaincodeStub stub = stub("QueryBalance", List.of("ACC-B1-001"), Map.of(
                "ACC-B1-001",
                "{\"id\":\"ACC-B1-001\",\"owner\":\"ClientA\",\"bankId\":\"Bank1\",\"balance\":1000}"
        ));

        Chaincode.Response response = chaincode.invoke(stub);

        assertEquals(200, response.getStatusCode());
        assertArrayEquals("1000".getBytes(StandardCharsets.UTF_8), response.getPayload());
    }

    @Test
    void getAccountReturnsTheAccountJsonInTheGatewayPayload() {
        String account = "{\"id\":\"ACC-B1-001\",\"balance\":1000}";
        ChaincodeStub stub = stub("getAccount", List.of("ACC-B1-001"),
                Map.of("ACC-B1-001", account));

        Chaincode.Response response = chaincode.invoke(stub);

        assertEquals(200, response.getStatusCode());
        assertArrayEquals(account.getBytes(StandardCharsets.UTF_8), response.getPayload());
    }

    @Test
    void missingAccountReturnsAnError() {
        ChaincodeStub stub = stub("QueryBalance", List.of("missing"), Map.of());

        Chaincode.Response response = chaincode.invoke(stub);

        assertEquals(500, response.getStatusCode());
        assertEquals("Account not found: missing", response.getMessage());
    }

    @Test
    void org1OwnerCanTransferAssetToOrg2() {
        Map<String, String> state = new HashMap<>();
        state.put("asset:ASSET-001", asset("ASSET-001", "Bank1 Treasury", "Org1MSP"));

        Chaincode.Response response = chaincode.invoke(stub(
                "TransferAsset", List.of("ASSET-001", "Bank2 Trade Desk", "Org2MSP"),
                state, "Org1MSP"));

        assertEquals(200, response.getStatusCode());
        assertTrue(state.get("asset:ASSET-001").contains("\"owner\":\"Bank2 Trade Desk\""));
        assertTrue(state.get("asset:ASSET-001").contains("\"ownerMsp\":\"Org2MSP\""));
        assertTrue(state.get("asset:ASSET-001").contains("\"lastTransferTxId\":\"tx-test-001\""));
    }

    @Test
    void org2OwnerCanTransferAssetBackToOrg1() {
        Map<String, String> state = new HashMap<>();
        state.put("asset:ASSET-001", asset("ASSET-001", "Bank2 Trade Desk", "Org2MSP"));

        Chaincode.Response response = chaincode.invoke(stub(
                "TransferAsset", List.of("ASSET-001", "Bank1 Treasury", "Org1MSP"),
                state, "Org2MSP"));

        assertEquals(200, response.getStatusCode());
        assertTrue(state.get("asset:ASSET-001").contains("\"ownerMsp\":\"Org1MSP\""));
    }

    @Test
    void nonOwnerMspCannotTransferAsset() {
        Map<String, String> state = new HashMap<>();
        state.put("asset:ASSET-001", asset("ASSET-001", "Bank1 Treasury", "Org1MSP"));

        Chaincode.Response response = chaincode.invoke(stub(
                "TransferAsset", List.of("ASSET-001", "Bank2 Trade Desk", "Org2MSP"),
                state, "Org2MSP"));

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getMessage().contains("Only current owner MSP Org1MSP"));
        assertTrue(state.get("asset:ASSET-001").contains("\"ownerMsp\":\"Org1MSP\""));
    }

    @Test
    void missingAssetReturnsSpecificError() {
        Chaincode.Response response = chaincode.invoke(stub(
                "ReadAsset", List.of("ASSET-MISSING"), new HashMap<>()));

        assertEquals(500, response.getStatusCode());
        assertEquals("Asset not found: ASSET-MISSING", response.getMessage());
    }

    @Test
    void ownershipTransferMustChangeOrganization() {
        Map<String, String> state = new HashMap<>();
        state.put("asset:ASSET-001", asset("ASSET-001", "Bank1 Treasury", "Org1MSP"));

        Chaincode.Response response = chaincode.invoke(stub(
                "TransferAsset", List.of("ASSET-001", "Another Org1 Owner", "Org1MSP"),
                state, "Org1MSP"));

        assertEquals(500, response.getStatusCode());
        assertEquals("Target MSP must differ from current owner MSP", response.getMessage());
    }

    @Test
    void tokenTransferRejectsSameAccount() {
        Map<String, String> state = new HashMap<>();
        state.put("ACC-B1-001", "{\"id\":\"ACC-B1-001\",\"owner\":\"ClientA\",\"bankId\":\"Bank1\",\"balance\":1000}");

        Chaincode.Response response = chaincode.invoke(stub(
                "Transfer", List.of("ACC-B1-001", "ACC-B1-001", "100"), state));

        assertEquals(500, response.getStatusCode());
        assertEquals("Source and destination accounts must be different", response.getMessage());
    }

    @Test
    void tokenTransferRejectsNonPositiveAmount() {
        Chaincode.Response response = chaincode.invoke(stub(
                "Transfer", List.of("ACC-B1-001", "ACC-B2-001", "0"), new HashMap<>()));

        assertEquals(500, response.getStatusCode());
        assertEquals("Transfer amount must be greater than zero", response.getMessage());
    }

    private static String asset(String id, String owner, String ownerMsp) {
        return "{\"id\":\"" + id + "\",\"description\":\"Trade document\","
                + "\"owner\":\"" + owner + "\",\"ownerMsp\":\"" + ownerMsp + "\","
                + "\"value\":25000,\"lastTransferTxId\":\"\"}";
    }

    private static ChaincodeStub stub(String function, List<String> parameters,
                                      Map<String, String> state) {
        return stub(function, parameters, new HashMap<>(state), "Org1MSP");
    }

    private static ChaincodeStub stub(String function, List<String> parameters,
                                      Map<String, String> state, String mspId) {
        return (ChaincodeStub) Proxy.newProxyInstance(
                ChaincodeStub.class.getClassLoader(),
                new Class<?>[]{ChaincodeStub.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getFunction":
                            return function;
                        case "getParameters":
                            return parameters;
                        case "getStringState":
                            return state.getOrDefault((String) args[0], "");
                        case "putStringState":
                            state.put((String) args[0], (String) args[1]);
                            return null;
                        case "getMspId":
                            return mspId;
                        case "getTxId":
                            return "tx-test-001";
                        case "setEvent":
                            return null;
                        case "toString":
                            return "TestChaincodeStub";
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
