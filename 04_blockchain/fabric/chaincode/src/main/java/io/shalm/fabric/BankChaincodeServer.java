package io.shalm.fabric;

import org.hyperledger.fabric.shim.ChaincodeServerProperties;
import org.hyperledger.fabric.shim.NettyChaincodeServer;

import java.net.InetSocketAddress;

public class BankChaincodeServer {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("CHAINCODE_SERVER_PORT", "9999"));

        BankChaincode chaincode = new BankChaincode(args);

        ChaincodeServerProperties props = new ChaincodeServerProperties();
        props.setServerAddress(new InetSocketAddress("0.0.0.0", port));

        new NettyChaincodeServer(chaincode, props).start();
    }
}
