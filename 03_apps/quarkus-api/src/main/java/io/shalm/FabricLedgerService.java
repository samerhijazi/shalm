package io.shalm;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hyperledger.fabric.protos.common.Block;
import org.hyperledger.fabric.protos.common.BlockMetadataIndex;
import org.hyperledger.fabric.protos.common.BlockchainInfo;
import org.hyperledger.fabric.protos.common.ChannelHeader;
import org.hyperledger.fabric.protos.common.Envelope;
import org.hyperledger.fabric.protos.common.HeaderType;
import org.hyperledger.fabric.protos.common.Payload;
import org.hyperledger.fabric.protos.peer.TxValidationCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Reads real Fabric block/ledger data via the built-in `qscc` system chaincode over the
 * existing Gateway connection — no chaincode redeploy, no new Maven dependency
 * (fabric-protos ships transitively with fabric-gateway).
 */
@ApplicationScoped
public class FabricLedgerService {

    @Inject
    FabricGatewayService fabric;

    public FabricLedgerService() {}

    FabricLedgerService(FabricGatewayService fabric) {
        this.fabric = fabric;
    }

    public ChainInfo getChainInfo() throws Exception {
        byte[] bytes = fabric.evaluateQscc("GetChainInfo", fabric.getChannelName());
        BlockchainInfo info = BlockchainInfo.parseFrom(bytes);
        return new ChainInfo(info.getHeight(), hex(info.getCurrentBlockHash()), hex(info.getPreviousBlockHash()));
    }

    public BlockDetail getBlock(long number) throws Exception {
        byte[] bytes = fabric.evaluateQscc("GetBlockByNumber", fabric.getChannelName(), String.valueOf(number));
        return parseBlock(bytes);
    }

    /**
     * Fetches up to {@code limit} of the most recent blocks, newest first. Each block is
     * a separate qscc round-trip — fine for a demo-scale ledger, not built for pagination
     * at scale.
     */
    public List<BlockDetail> getRecentBlocks(int limit) throws Exception {
        ChainInfo chainInfo = getChainInfo();
        long latest = chainInfo.height - 1;
        long oldest = Math.max(0, latest - limit + 1);
        List<BlockDetail> blocks = new ArrayList<>();
        for (long n = latest; n >= oldest; n--) {
            blocks.add(getBlock(n));
        }
        return blocks;
    }

    private BlockDetail parseBlock(byte[] bytes) throws Exception {
        Block block = Block.parseFrom(bytes);

        long number = block.getHeader().getNumber();
        String previousHash = hex(block.getHeader().getPreviousHash());
        String dataHash = hex(block.getHeader().getDataHash());

        List<ByteString> envelopes = block.getData().getDataList();
        ByteString txFilterBytes = block.getMetadata().getMetadataCount() > BlockMetadataIndex.TRANSACTIONS_FILTER_VALUE
                ? block.getMetadata().getMetadata(BlockMetadataIndex.TRANSACTIONS_FILTER_VALUE)
                : ByteString.EMPTY;
        byte[] txFilter = txFilterBytes.toByteArray();

        List<BlockTransaction> transactions = new ArrayList<>();
        String blockTimestamp = null;
        for (int i = 0; i < envelopes.size(); i++) {
            BlockTransaction tx = parseTransaction(envelopes.get(i), i < txFilter.length ? txFilter[i] : -1);
            transactions.add(tx);
            if (blockTimestamp == null) {
                blockTimestamp = tx.timestamp;
            }
        }

        return new BlockDetail(number, envelopes.size(), dataHash, previousHash, blockTimestamp,
                fabric.getChannelName(), transactions);
    }

    private BlockTransaction parseTransaction(ByteString envelopeBytes, int validationCodeByte) {
        try {
            Envelope envelope = Envelope.parseFrom(envelopeBytes);
            Payload payload = Payload.parseFrom(envelope.getPayload());
            ChannelHeader channelHeader = ChannelHeader.parseFrom(payload.getHeader().getChannelHeader());

            String txId = channelHeader.getTxId();
            HeaderType type = HeaderType.forNumber(channelHeader.getType());
            String typeName = type != null ? type.name() : "UNKNOWN";
            String timestamp = toIsoTimestamp(channelHeader.getTimestamp());
            String validationCode = validationCodeName(validationCodeByte);

            return new BlockTransaction(txId, typeName, validationCode, timestamp);
        } catch (Exception e) {
            return new BlockTransaction(null, "UNKNOWN", validationCodeName(validationCodeByte), null);
        }
    }

    private static String validationCodeName(int code) {
        if (code < 0) {
            return "UNKNOWN";
        }
        TxValidationCode vc = TxValidationCode.forNumber(code & 0xFF);
        return vc != null ? vc.name() : "UNKNOWN";
    }

    private static String toIsoTimestamp(Timestamp ts) {
        if (ts == null || (ts.getSeconds() == 0 && ts.getNanos() == 0)) {
            return null;
        }
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos()).toString();
    }

    private static String hex(ByteString bytes) {
        return HexFormat.of().formatHex(bytes.toByteArray());
    }
}
