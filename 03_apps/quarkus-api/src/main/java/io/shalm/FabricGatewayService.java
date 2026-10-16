package io.shalm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.client.Status;
import org.hyperledger.fabric.client.SubmittedTransaction;
import org.hyperledger.fabric.client.Transaction;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;
import org.jboss.logging.Logger;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class FabricGatewayService {

    private static final Logger LOG = Logger.getLogger(FabricGatewayService.class);

    @ConfigProperty(name = "fabric.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "fabric.org1.peer.host", defaultValue = "peer0-org1.fabric.svc.cluster.local")
    String org1PeerHost;

    @ConfigProperty(name = "fabric.org1.peer.port", defaultValue = "7051")
    int org1PeerPort;

    @ConfigProperty(name = "fabric.org1.msp.id", defaultValue = "Org1MSP")
    String org1MspId;

    @ConfigProperty(name = "fabric.org1.cert.path", defaultValue = "/fabric-crypto/org1/admin-cert.pem")
    String org1CertPath;

    @ConfigProperty(name = "fabric.org1.key.path", defaultValue = "/fabric-crypto/org1/admin-key.pem")
    String org1KeyPath;

    @ConfigProperty(name = "fabric.org2.peer.host", defaultValue = "peer0-org2.fabric.svc.cluster.local")
    String org2PeerHost;

    @ConfigProperty(name = "fabric.org2.peer.port", defaultValue = "7051")
    int org2PeerPort;

    @ConfigProperty(name = "fabric.org2.msp.id", defaultValue = "Org2MSP")
    String org2MspId;

    @ConfigProperty(name = "fabric.org2.cert.path", defaultValue = "/fabric-crypto/org2/admin-cert.pem")
    String org2CertPath;

    @ConfigProperty(name = "fabric.org2.key.path", defaultValue = "/fabric-crypto/org2/admin-key.pem")
    String org2KeyPath;

    @ConfigProperty(name = "fabric.channel", defaultValue = "mychannel")
    String channelName;

    @ConfigProperty(name = "fabric.chaincode", defaultValue = "bank-transfer")
    String chaincodeName;

    @ConfigProperty(name = "fabric.health.account", defaultValue = "ACC-B1-001")
    String healthAccount;

    @ConfigProperty(name = "fabric.chaincode.version", defaultValue = "unknown")
    String chaincodeVersion;

    @ConfigProperty(name = "fabric.endorsing.orgs", defaultValue = "Org1MSP,Org2MSP")
    String endorsingOrgsCsv;

    @Inject
    ObjectMapper objectMapper;

    private final Map<String, OrganizationGateway> organizations = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        if (!enabled) {
            LOG.info("Fabric Gateway disabled — /fabric/* endpoints return 503");
            return;
        }

        connectOrganization(org1MspId, org1PeerHost, org1PeerPort, org1CertPath, org1KeyPath);
        connectOrganization(org2MspId, org2PeerHost, org2PeerPort, org2CertPath, org2KeyPath);

        if (organizations.isEmpty()) {
            LOG.warn("No Fabric organization gateway is available");
        }
    }

    private void connectOrganization(String mspId, String peerHost, int peerPort,
                                     String certPath, String keyPath) {
        try {
            Reader certReader = Files.newBufferedReader(Path.of(certPath));
            X509Certificate cert = Identities.readX509Certificate(certReader);

            Reader keyReader = Files.newBufferedReader(Path.of(keyPath));
            PrivateKey privateKey = Identities.readPrivateKey(keyReader);

            ManagedChannel grpcChannel = NettyChannelBuilder
                    .forAddress(peerHost, peerPort)
                    .usePlaintext()
                    .build();

            Gateway gateway = Gateway.newInstance()
                    .identity(new X509Identity(mspId, cert))
                    .signer(Signers.newPrivateKeySigner(privateKey))
                    .connection(grpcChannel)
                    .evaluateOptions(o -> o.withDeadlineAfter(5, TimeUnit.SECONDS))
                    .endorseOptions(o -> o.withDeadlineAfter(15, TimeUnit.SECONDS))
                    .submitOptions(o -> o.withDeadlineAfter(5, TimeUnit.SECONDS))
                    .commitStatusOptions(o -> o.withDeadlineAfter(60, TimeUnit.SECONDS))
                    .connect();

            Network network = gateway.getNetwork(channelName);
            organizations.put(mspId, new OrganizationGateway(
                    grpcChannel, gateway, network.getContract(chaincodeName), network.getContract("qscc")));
            LOG.infof("Fabric Gateway connected for %s: %s:%d / %s / %s",
                    mspId, peerHost, peerPort, channelName, chaincodeName);
        } catch (Exception e) {
            LOG.warnf("Fabric Gateway unavailable for %s at %s:%d: %s",
                    mspId, peerHost, peerPort, e.getMessage());
        }
    }

    public boolean isAvailable() {
        return !organizations.isEmpty();
    }

    public boolean isOwnershipTransferAvailable() {
        return organizations.containsKey(org1MspId) && organizations.containsKey(org2MspId);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getChaincodeName() {
        return chaincodeName;
    }

    public String getChaincodeVersion() {
        return chaincodeVersion;
    }

    public String getMspId() {
        return org1MspId;
    }

    public String getPeerHost() {
        return org1PeerHost;
    }

    public int getPeerPort() {
        return org1PeerPort;
    }

    public int queryBalance(String accountId) throws Exception {
        byte[] result = primary().contract.evaluateTransaction("QueryBalance", accountId);
        return Integer.parseInt(new String(result, StandardCharsets.UTF_8).trim());
    }

    public int checkHealth() throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("Fabric Gateway is not connected");
        }
        return queryBalance(healthAccount);
    }

    public byte[] evaluateQscc(String function, String... args) throws Exception {
        return primary().qsccContract.evaluateTransaction(function, args);
    }

    public FabricTransferResult transfer(String fromId, String toId, int amount) throws Exception {
        return submit(org1(), "Transfer", fromId, toId, String.valueOf(amount));
    }

    public List<OwnedAsset> listAssets() throws Exception {
        byte[] result = primary().contract.evaluateTransaction("GetAllAssets");
        return objectMapper.readValue(result, new TypeReference<List<OwnedAsset>>() {});
    }

    public OwnedAsset getAsset(String assetId) throws Exception {
        return getAsset(primary(), assetId);
    }

    private OwnedAsset getAsset(OrganizationGateway organization, String assetId) throws Exception {
        byte[] result = organization.contract.evaluateTransaction("ReadAsset", assetId);
        return objectMapper.readValue(result, OwnedAsset.class);
    }

    public OwnershipTransferResponse transferAsset(String assetId, String newOwner,
                                                    String newOwnerMsp) throws Exception {
        if (!isOwnershipTransferAvailable()) {
            throw new IllegalStateException("Both Org1MSP and Org2MSP gateway identities are required");
        }

        OwnedAsset current = getAsset(assetId);
        OrganizationGateway ownerGateway = organizations.get(current.ownerMsp);
        if (ownerGateway == null) {
            throw new IllegalStateException("No gateway identity available for current owner " + current.ownerMsp);
        }

        FabricTransferResult result = submit(ownerGateway, "TransferAsset", assetId, newOwner, newOwnerMsp);
        // Read from the submitting peer: getStatus() confirms its commit, while the
        // other peer may still be applying the block for a few milliseconds.
        OwnedAsset updated = getAsset(ownerGateway, assetId);
        return new OwnershipTransferResponse(updated, result.transactionId, result.blockNumber,
                result.validationCode, "committed", current.ownerMsp);
    }

    private FabricTransferResult submit(OrganizationGateway organization, String function,
                                        String... args) throws Exception {
        Transaction tx = organization.contract.newProposal(function)
                .addArguments(args)
                .setEndorsingOrganizations(endorsingOrgsCsv.split(","))
                .build()
                .endorse();
        SubmittedTransaction submitted = tx.submitAsync();
        Status status = submitted.getStatus();
        if (!status.isSuccessful()) {
            throw new IllegalStateException("Fabric transaction failed validation: " + status.getCode());
        }
        return new FabricTransferResult(status.getTransactionId(), status.getBlockNumber(),
                status.getCode().name(), true);
    }

    private OrganizationGateway primary() {
        OrganizationGateway primary = organizations.get(org1MspId);
        if (primary != null) {
            return primary;
        }
        return organizations.values().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Fabric Gateway is not connected"));
    }

    private OrganizationGateway org1() {
        OrganizationGateway gateway = organizations.get(org1MspId);
        if (gateway == null) {
            throw new IllegalStateException("Org1MSP Fabric Gateway is not connected");
        }
        return gateway;
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        for (OrganizationGateway organization : organizations.values()) {
            organization.gateway.close();
            organization.grpcChannel.shutdownNow();
        }
        for (OrganizationGateway organization : organizations.values()) {
            organization.grpcChannel.awaitTermination(5, TimeUnit.SECONDS);
        }
        organizations.clear();
    }

    private static final class OrganizationGateway {
        private final ManagedChannel grpcChannel;
        private final Gateway gateway;
        private final Contract contract;
        private final Contract qsccContract;

        private OrganizationGateway(ManagedChannel grpcChannel, Gateway gateway,
                                    Contract contract, Contract qsccContract) {
            this.grpcChannel = grpcChannel;
            this.gateway = gateway;
            this.contract = contract;
            this.qsccContract = qsccContract;
        }
    }
}
