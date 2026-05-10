package io.shalm;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class FabricGatewayService {

    private static final Logger LOG = Logger.getLogger(FabricGatewayService.class);

    @ConfigProperty(name = "fabric.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "fabric.peer.host", defaultValue = "peer0-org1.fabric.svc.cluster.local")
    String peerHost;

    @ConfigProperty(name = "fabric.peer.port", defaultValue = "7051")
    int peerPort;

    @ConfigProperty(name = "fabric.msp.id", defaultValue = "Org1MSP")
    String mspId;

    @ConfigProperty(name = "fabric.channel", defaultValue = "mychannel")
    String channelName;

    @ConfigProperty(name = "fabric.chaincode", defaultValue = "bank-transfer")
    String chaincodeName;

    @ConfigProperty(name = "fabric.cert.path", defaultValue = "/fabric-crypto/admin-cert.pem")
    String certPath;

    @ConfigProperty(name = "fabric.key.path", defaultValue = "/fabric-crypto/admin-key.pem")
    String keyPath;

    private Gateway gateway;
    private ManagedChannel grpcChannel;
    private Contract contract;
    private boolean available = false;

    @Inject
    void init() {
        if (!enabled) {
            LOG.info("Fabric Gateway disabled — /fabric/* endpoints return 503");
            return;
        }
        try {
            connect();
        } catch (Exception e) {
            LOG.warnf("Fabric Gateway unavailable at startup: %s. /fabric/* endpoints return 503.", e.getMessage());
        }
    }

    private void connect() throws Exception {
        Reader certReader = Files.newBufferedReader(Path.of(certPath));
        X509Certificate cert = Identities.readX509Certificate(certReader);

        Reader keyReader = Files.newBufferedReader(Path.of(keyPath));
        PrivateKey privateKey = Identities.readPrivateKey(keyReader);

        grpcChannel = NettyChannelBuilder
                .forAddress(peerHost, peerPort)
                .usePlaintext()
                .build();

        gateway = Gateway.newInstance()
                .identity(new X509Identity(mspId, cert))
                .signer(Signers.newPrivateKeySigner(privateKey))
                .connection(grpcChannel)
                .evaluateOptions(o -> o.withDeadlineAfter(5, TimeUnit.SECONDS))
                .endorseOptions(o -> o.withDeadlineAfter(15, TimeUnit.SECONDS))
                .submitOptions(o -> o.withDeadlineAfter(5, TimeUnit.SECONDS))
                .commitStatusOptions(o -> o.withDeadlineAfter(60, TimeUnit.SECONDS))
                .connect();

        Network network = gateway.getNetwork(channelName);
        contract = network.getContract(chaincodeName);
        available = true;
        LOG.infof("Fabric Gateway connected: %s:%d / %s / %s", peerHost, peerPort, channelName, chaincodeName);
    }

    public boolean isAvailable() {
        return available;
    }

    public int queryBalance(String accountId) throws Exception {
        byte[] result = contract.evaluateTransaction("QueryBalance", accountId);
        return Integer.parseInt(new String(result).trim());
    }

    public void transfer(String fromId, String toId, int amount) throws Exception {
        contract.submitTransaction("Transfer", fromId, toId, String.valueOf(amount));
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        if (gateway != null) gateway.close();
        if (grpcChannel != null) grpcChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
}
