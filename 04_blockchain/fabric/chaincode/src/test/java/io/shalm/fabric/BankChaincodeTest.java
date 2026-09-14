package io.shalm.fabric;

import org.hyperledger.fabric.shim.Chaincode;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static ChaincodeStub stub(String function, List<String> parameters,
                                      Map<String, String> state) {
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
                        case "toString":
                            return "TestChaincodeStub";
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
