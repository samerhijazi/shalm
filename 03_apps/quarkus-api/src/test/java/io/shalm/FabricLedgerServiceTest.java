package io.shalm;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import org.hyperledger.fabric.protos.common.Block;
import org.hyperledger.fabric.protos.common.BlockData;
import org.hyperledger.fabric.protos.common.BlockHeader;
import org.hyperledger.fabric.protos.common.BlockMetadata;
import org.hyperledger.fabric.protos.common.BlockchainInfo;
import org.hyperledger.fabric.protos.common.ChannelHeader;
import org.hyperledger.fabric.protos.common.Envelope;
import org.hyperledger.fabric.protos.common.Header;
import org.hyperledger.fabric.protos.common.HeaderType;
import org.hyperledger.fabric.protos.common.Payload;
import org.hyperledger.fabric.protos.peer.TxValidationCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FabricLedgerServiceTest {

    private static final byte[] PREV_HASH = {0x0a, 0x0b, 0x0c};
    private static final byte[] DATA_HASH = {(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef};
    private static final byte[] CURRENT_HASH = {0x01, 0x02};
    private static final byte[] PREVIOUS_CHAIN_HASH = {0x03, 0x04};

    @Test
    void getChainInfo_parsesHeightAndHashes() throws Exception {
        BlockchainInfo info = BlockchainInfo.newBuilder()
                .setHeight(11)
                .setCurrentBlockHash(ByteString.copyFrom(CURRENT_HASH))
                .setPreviousBlockHash(ByteString.copyFrom(PREVIOUS_CHAIN_HASH))
                .build();

        FabricLedgerService service = new FabricLedgerService(fakeGateway(info.toByteArray(), null));
        ChainInfo chainInfo = service.getChainInfo();

        assertEquals(11, chainInfo.height);
        assertEquals("0102", chainInfo.currentBlockHash);
        assertEquals("0304", chainInfo.previousBlockHash);
    }

    @Test
    void getBlock_parsesHeaderTxIdTypeAndValidationCode() throws Exception {
        String txId = "abc123";
        long seconds = 1_700_000_000L;

        ChannelHeader channelHeader = ChannelHeader.newBuilder()
                .setTxId(txId)
                .setType(HeaderType.ENDORSER_TRANSACTION.getNumber())
                .setTimestamp(Timestamp.newBuilder().setSeconds(seconds).setNanos(0).build())
                .build();
        Header header = Header.newBuilder()
                .setChannelHeader(channelHeader.toByteString())
                .build();
        Payload payload = Payload.newBuilder().setHeader(header).build();
        Envelope envelope = Envelope.newBuilder().setPayload(payload.toByteString()).build();

        BlockHeader blockHeader = BlockHeader.newBuilder()
                .setNumber(7)
                .setPreviousHash(ByteString.copyFrom(PREV_HASH))
                .setDataHash(ByteString.copyFrom(DATA_HASH))
                .build();
        BlockData blockData = BlockData.newBuilder().addData(envelope.toByteString()).build();

        // Fabric's real metadata array always has 5 slots (SIGNATURES, LAST_CONFIG,
        // TRANSACTIONS_FILTER, ORDERER, COMMIT_HASH); only TRANSACTIONS_FILTER matters here.
        BlockMetadata metadata = BlockMetadata.newBuilder()
                .addMetadata(ByteString.EMPTY)
                .addMetadata(ByteString.EMPTY)
                .addMetadata(ByteString.copyFrom(new byte[]{(byte) TxValidationCode.VALID.getNumber()}))
                .addMetadata(ByteString.EMPTY)
                .addMetadata(ByteString.EMPTY)
                .build();

        Block block = Block.newBuilder()
                .setHeader(blockHeader)
                .setData(blockData)
                .setMetadata(metadata)
                .build();

        FabricLedgerService service = new FabricLedgerService(fakeGateway(null, block.toByteArray()));
        BlockDetail detail = service.getBlock(7);

        assertEquals(7, detail.number);
        assertEquals(1, detail.txCount);
        assertEquals("0a0b0c", detail.previousHash);
        assertEquals("deadbeef", detail.dataHash);
        assertEquals("mychannel", detail.channel);
        assertNotNull(detail.timestamp);

        List<BlockTransaction> txs = detail.transactions;
        assertEquals(1, txs.size());
        assertEquals(txId, txs.get(0).txId);
        assertEquals("ENDORSER_TRANSACTION", txs.get(0).type);
        assertEquals("VALID", txs.get(0).validationCode);
    }

    @Test
    void getBlock_unparsableTransactionFallsBackToUnknownInsteadOfThrowing() throws Exception {
        BlockData blockData = BlockData.newBuilder().addData(ByteString.copyFrom(new byte[]{1, 2, 3})).build();
        BlockHeader blockHeader = BlockHeader.newBuilder().setNumber(1).build();
        BlockMetadata metadata = BlockMetadata.newBuilder()
                .addMetadata(ByteString.EMPTY)
                .addMetadata(ByteString.EMPTY)
                .addMetadata(ByteString.copyFrom(new byte[]{0}))
                .build();
        Block block = Block.newBuilder().setHeader(blockHeader).setData(blockData).setMetadata(metadata).build();

        FabricLedgerService service = new FabricLedgerService(fakeGateway(null, block.toByteArray()));
        BlockDetail detail = service.getBlock(1);

        assertEquals(1, detail.txCount);
        assertEquals("UNKNOWN", detail.transactions.get(0).type);
    }

    private static FabricGatewayService fakeGateway(byte[] chainInfoResponse, byte[] blockResponse) {
        return new FabricGatewayService() {
            @Override
            public String getChannelName() {
                return "mychannel";
            }

            @Override
            public byte[] evaluateQscc(String function, String... args) {
                if ("GetChainInfo".equals(function)) {
                    return chainInfoResponse;
                }
                return blockResponse;
            }
        };
    }
}
