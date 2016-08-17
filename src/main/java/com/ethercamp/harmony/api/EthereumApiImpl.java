package com.ethercamp.harmony.api;

import com.ethercamp.harmony.api.data.ParsedBlock;
import com.ethercamp.harmony.api.data.SyncStatus;
import com.ethercamp.harmony.jsonrpc.TypeConverter;
import com.ethercamp.harmony.keystore.Keystore;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.TransactionStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.manager.WorldManager;
import org.ethereum.mine.BlockMiner;
import org.ethereum.net.client.Capability;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.sync.SyncManager;
import org.ethereum.util.BuildInfo;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static java.lang.Math.max;

/**
 * Created by Stan Reshetnyk on 03.08.16.
 */
@Component
@Slf4j(topic = "api")
public class EthereumApiImpl {

    private static final String BLOCK_LATEST = "latest";

    @Autowired
    Keystore keystore;

    @Autowired
    SystemProperties config;

    @Autowired
    ConfigCapabilities configCapabilities;

    @Autowired
    WorldManager worldManager;

    @Autowired
    Repository repository;

    @Autowired
    BlockchainImpl blockchain;

    @Autowired
    Ethereum eth;

    @Autowired
    PeerServer peerServer;

    @Autowired
    SyncManager syncManager;

    @Autowired
    TransactionStore txStore;

    @Autowired
    ChannelManager channelManager;

    @Autowired
    CompositeEthereumListener compositeEthereumListener;

    @Autowired
    BlockMiner blockMiner;

    @Autowired
    TransactionStore transactionStore;

    @Autowired
    PendingStateImpl pendingState;

    @Autowired
    private Ethereum ethereum;


    /**
     * State fields
     */
    protected volatile long initialBlockNumber;

    protected volatile SyncStatus syncStatus = SyncStatus.LONG_SYNC;


    @PostConstruct
    private void init() {
        initialBlockNumber = blockchain.getBestBlock().getNumber();

        if (!config.isSyncEnabled()) {
            syncStatus = SyncStatus.DISABLED;
        } else {
            ethereum.addListener(new EthereumListenerAdapter() {
                @Override
                public void onSyncDone() {
                    log.info("Sync done");
                    syncStatus = SyncStatus.SHORT_SYNC;
                }
            });
        }
    }

    public String web3_clientVersion() {
        String s = "EthereumJ" + "/v" + config.projectVersion() + "/" +
                System.getProperty("os.name") + "/Java1.7/" + config.projectVersionModifier() + "-" + BuildInfo.buildHash;
        return s;
    }

    public String web3_sha3(String data) throws Exception {
        byte[] result = HashUtil.sha3(TypeConverter.StringHexToByteArray(data));
        return TypeConverter.toJsonHex(result);
    }

    public String net_version() {
        return eth_protocolVersion();
    }

    public int net_peerCount() {
        return channelManager.getActivePeers().size();
    }

    public boolean net_listening() {
        return peerServer.isListening();
    }

    public String eth_protocolVersion(){
        int version = 0;
        for (Capability capability : configCapabilities.getConfigCapabilities()) {
            if (capability.isEth()) {
                version = max(version, capability.getVersion());
            }
        }
        return Integer.toString(version);
    }

    public long getInitialBlockNumber(){
        return initialBlockNumber;
    }

    public long getBestBlockNumber(){
        return blockchain.getBestBlock().getNumber();
    }

    /**
     * @return found block or null
     */
    public Block getBlock(long blockNumber){
        return blockchain.getBlockByNumber(blockNumber);
    }

    public long getLastKnownBlockNumber(){
        return syncManager.getLastKnownBlockNumber();
    }

    public byte[] eth_coinbase() {
        return blockchain.getMinerCoinbase();
    }

    public boolean eth_mining() {
        return blockMiner.isMining();
    }

    public String eth_hashrate() {
        return null;
    }

    public long eth_gasPrice(){
        return eth.getGasPrice();
    }

    public String getNodeId() {
        return Hex.toHexString(config.nodeId());
    }

    public int getActivePeersCount() {
        return channelManager.getActivePeers().size();
    }

    public SyncStatus getSyncStatus() {
        return syncStatus;
    }

    public int getEthPort() {
        return config.listenPort();
    }

//    public String[] eth_accounts() {
//        return personal_listAccounts();
//    }
//
//    public long eth_blockNumber(){
//        Block bestBlock = blockchain.getBestBlock();
//        return bestBlock != null ? bestBlock.getNumber() : 0l;
//    }
//
//    public BigInteger eth_getBalance(String address, String blockId) throws Exception {
////            Objects.requireNonNull(address, "address is required");
//        blockId = blockId == null ? BLOCK_LATEST : blockId;
//
//        byte[] addressAsByteArray = TypeConverter.StringHexToByteArray(address);
//        BigInteger balance = getRepoByJsonBlockId(blockId).getBalance(addressAsByteArray);
//        return balance;
//    }
//
//    public String eth_getLastBalance(String address) throws Exception {
//        String s = null;
//        try {
//            return s = eth_getBalance(address, BLOCK_LATEST);
//        } finally {
//            if (log.isDebugEnabled()) log.debug("eth_getLastBalance(" + address + "): " + s);
//        }
//    }
//
//    @Override
//    public String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception {
//        String s = null;
//        try {
//            byte[] addressAsByteArray = StringHexToByteArray(address);
//            DataWord storageValue = getRepoByJsonBlockId(blockId).
//                    getStorageValue(addressAsByteArray, new DataWord(StringHexToByteArray(storageIdx)));
//            return s = TypeConverter.toJsonHex(storageValue.getData());
//        } finally {
//            if (log.isDebugEnabled()) log.debug("eth_getStorageAt(" + address + ", " + storageIdx + ", " + blockId + "): " + s);
//        }
//    }
//
//    @Override
//    public String eth_getTransactionCount(String address, String blockId) throws Exception {
//        String s = null;
//        try {
//            byte[] addressAsByteArray = TypeConverter.StringHexToByteArray(address);
//            BigInteger nonce = getRepoByJsonBlockId(blockId).getNonce(addressAsByteArray);
//            return s = TypeConverter.toJsonHex(nonce);
//        } finally {
//            if (log.isDebugEnabled()) log.debug("eth_getTransactionCount(" + address + ", " + blockId + "): " + s);
//        }
//    }
//
//    public String eth_getBlockTransactionCountByHash(String blockHash) throws Exception {
//        String s = null;
//        try {
//            Block b = getBlockByJSonHash(blockHash);
//            if (b == null) return null;
//            long n = b.getTransactionsList().size();
//            return s = TypeConverter.toJsonHex(n);
//        } finally {
//            if (log.isDebugEnabled()) log.debug("eth_getBlockTransactionCountByHash(" + blockHash + "): " + s);
//        }
//    }
//
//    public String eth_getBlockTransactionCountByNumber(String bnOrId) throws Exception {
//        String s = null;
//        try {
//            List<Transaction> list = getTransactionsByJsonBlockId(bnOrId);
//            if (list == null) return null;
//            long n = list.size();
//            return s = TypeConverter.toJsonHex(n);
//        } finally {
//            if (log.isDebugEnabled()) log.debug("eth_getBlockTransactionCountByNumber(" + bnOrId + "): " + s);
//        }
//    }
//
//    public String eth_getUncleCountByBlockHash(String blockHash) throws Exception {
//        String s = null;
//        try {
//            Block b = getBlockByJSonHash(blockHash);
//            if (b == null) return null;
//            long n = b.getUncleList().size();
//            return s = TypeConverter.toJsonHex(n);
//        } finally {
//            if (log.isDebugEnabled()) log.debug("eth_getUncleCountByBlockHash(" + blockHash + "): " + s);
//        }
//    }
//
//    public String eth_getUncleCountByBlockNumber(String bnOrId) throws Exception {
//        String s = null;
//        try {
//            Block b = getByJsonBlockId(bnOrId);
//            if (b == null) return null;
//            long n = b.getUncleList().size();
//            return s = TypeConverter.toJsonHex(n);
//        } finally {
//            if (log.isDebugEnabled()) log.debug("eth_getUncleCountByBlockNumber(" + bnOrId + "): " + s);
//        }
//    }
//
//    public String eth_getCode(String address, String blockId) throws Exception {
//        String s = null;
//        try {
//            byte[] addressAsByteArray = TypeConverter.StringHexToByteArray(address);
//            byte[] code = getRepoByJsonBlockId(blockId).getCode(addressAsByteArray);
//            return s = TypeConverter.toJsonHex(code);
//        } finally {
//            if (log.isDebugEnabled()) log.debug("eth_getCode(" + address + ", " + blockId + "): " + s);
//        }
//    }

}
