package com.ethercamp.harmony.jsonrpc;

import com.ethercamp.harmony.keystore.KeystoreManager;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.TransactionStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.manager.WorldManager;
import org.ethereum.mine.BlockMiner;
import org.ethereum.net.client.Capability;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.sync.SyncManager;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static org.ethereum.crypto.HashUtil.sha3;
import static com.ethercamp.harmony.jsonrpc.TypeConverter.*;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ethereum.util.ByteUtil.bigIntegerToBytes;

/**
 * @author Anton Nashatyrev
 */
@Slf4j(topic = "jsonrpc")
public class JsonRpcImpl implements JsonRpc {

    @Autowired
    KeystoreManager keystoreManager;

    public class BinaryCallArguments {
        public long nonce;
        public long gasPrice;
        public long gasLimit;
        public String toAddress;
        public long value;
        public byte[] data;
        public void setArguments(CallArguments args) throws Exception {
            nonce = 0;
            if (args.nonce != null && args.nonce.length() != 0)
                nonce = JSonHexToLong(args.nonce);

            gasPrice = 0;
            if (args.gasPrice != null && args.gasPrice.length()!=0)
                gasPrice = JSonHexToLong(args.gasPrice);

            gasLimit = 4_000_000;
            if (args.gasLimit != null && args.gasLimit.length()!=0)
                gasLimit = JSonHexToLong(args.gasLimit);

            toAddress = null;
            if (args.to != null && !args.to.isEmpty())
                toAddress = JSonHexToHex(args.to);

            value=0;
            if (args.value != null && args.value.length()!=0)
                value = JSonHexToLong(args.value);

            data = null;

            if (args.data != null && args.data.length()!=0)
                data = TypeConverter.StringHexToByteArray(args.data);
        }
    }

    @Autowired
    SystemProperties config;

    @Autowired
    ConfigCapabilities configCapabilities;

    @Autowired
    public WorldManager worldManager;

    @Autowired
    public Repository repository;

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

    long initialBlockNumber;

//    Map<ByteArrayWrapper, Account> accounts = new HashMap<>();
    AtomicInteger filterCounter = new AtomicInteger(1);
    Map<Integer, Filter> installedFilters = new Hashtable<>();

    @PostConstruct
    private void init() {
        initialBlockNumber = blockchain.getBestBlock().getNumber();

        compositeEthereumListener.addListener(new EthereumListenerAdapter() {
            @Override
            public void onBlock(Block block, List<TransactionReceipt> receipts) {
                for (Filter filter : installedFilters.values()) {
                    filter.newBlockReceived(block);
                }
            }

            @Override
            public void onPendingTransactionsReceived(List<Transaction> transactions) {
                for (Filter filter : installedFilters.values()) {
                    for (Transaction tx : transactions) {
                        filter.newPendingTx(tx);
                    }
                }
            }
        });

    }

    private long JSonHexToLong(String x) throws Exception {
        if (!x.startsWith("0x"))
            throw new Exception("Incorrect hex syntax");
        x = x.substring(2);
        return Long.parseLong(x, 16);
    }

    private int JSonHexToInt(String x) throws Exception {
        if (!x.startsWith("0x"))
            throw new Exception("Incorrect hex syntax");
        x = x.substring(2);
        return Integer.parseInt(x, 16);
    }

    private String JSonHexToHex(String x) throws Exception {
        if (!x.startsWith("0x"))
            throw new Exception("Incorrect hex syntax");
        x = x.substring(2);
        return x;
    }

    private Block getBlockByJSonHash(String blockHash) throws Exception {
        byte[] bhash = TypeConverter.StringHexToByteArray(blockHash);
        return worldManager.getBlockchain().getBlockByHash(bhash);
    }

    private Block getByJsonBlockId(String id) {
        if ("earliest".equalsIgnoreCase(id)) {
            return blockchain.getBlockByNumber(0);
        } else if ("latest".equalsIgnoreCase(id)) {
            return blockchain.getBestBlock();
        } else if ("pending".equalsIgnoreCase(id)) {
            return null;
        } else {
            long blockNumber = StringHexToBigInteger(id).longValue();
            return blockchain.getBlockByNumber(blockNumber);
        }
    }

    private Repository getRepoByJsonBlockId(String id) {
        if ("pending".equalsIgnoreCase(id)) {
            return pendingState.getRepository();
        } else {
            Block block = getByJsonBlockId(id);
            return this.repository.getSnapshotTo(block.getStateRoot());
        }
    }

    private List<Transaction> getTransactionsByJsonBlockId(String id) {
        if ("pending".equalsIgnoreCase(id)) {
            return pendingState.getPendingTransactions();
        } else {
            Block block = getByJsonBlockId(id);
            return block != null ? block.getTransactionsList() : null;
        }
    }

    protected Account getAccount(String address) throws Exception {
        return keystoreManager.loadStoredKey(address, "123")
                .map(key -> {
                    Account account = new Account();
                    account.init(key);
                    return account;
                }).orElse(null);
//        return accounts.get(new ByteArrayWrapper(StringHexToByteArray(address)));
    }

    protected Account addAccount(String password) {
        return addAccount(ECKey.fromPrivate(sha3(password.getBytes())));
    }

    protected Account addAccount(ECKey key) {
        Account account = new Account();
        account.init(key);
        keystoreManager.storeKey(key, "");
//        accounts.put(new ByteArrayWrapper(account.getAddress()), account);
        return account;
    }

    public String web3_clientVersion() {

        String s = "EthereumJ" + "/v" + config.projectVersion() + "/" +
                System.getProperty("os.name") + "/Java1.7/" + config.projectVersionModifier() + "-" + BuildInfo.buildHash;
        if (log.isDebugEnabled()) log.debug("web3_clientVersion(): " + s);
        return s;
    };

    public String web3_sha3(String data) throws Exception {
        String s = null;
        try {
            byte[] result = HashUtil.sha3(TypeConverter.StringHexToByteArray(data));
            return s = TypeConverter.toJsonHex(result);
        } finally {
            if (log.isDebugEnabled()) log.debug("web3_sha3(" + data + "): " + s);
        }
    }

    public String net_version() {
        String s = null;
        try {
            return s = eth_protocolVersion();
        } finally {
            if (log.isDebugEnabled()) log.debug("net_version(): " + s);
        }
    }

    public String net_peerCount(){
        String s = null;
        try {
            int n = channelManager.getActivePeers().size();
            return s = TypeConverter.toJsonHex(n);
        } finally {
            if (log.isDebugEnabled()) log.debug("net_peerCount(): " + s);
        }
    }

    public boolean net_listening() {
        Boolean s = null;
        try {
            return s = peerServer.isListening();
        }finally {
            if (log.isDebugEnabled()) log.debug("net_listening(): " + s);
        }
    }

    public String eth_protocolVersion(){
        String s = null;
        try {
            int version = 0;
            for (Capability capability : configCapabilities.getConfigCapabilities()) {
                if (capability.isEth()) {
                    version = max(version, capability.getVersion());
                }
            }
            return s = Integer.toString(version);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_protocolVersion(): " + s);
        }
    }

    public SyncingResult eth_syncing(){
        SyncingResult s = new SyncingResult();
        try {
            s.startingBlock = TypeConverter.toJsonHex(initialBlockNumber);
            s.currentBlock = TypeConverter.toJsonHex(blockchain.getBestBlock().getNumber());
            s.highestBlock = TypeConverter.toJsonHex(syncManager.getLastKnownBlockNumber());

            return s;
        }finally {
            if (log.isDebugEnabled()) log.debug("eth_syncing(): " + s);
        }
    };

    public String eth_coinbase() {
        String s = null;
        try {
            return s = toJsonHex(blockchain.getMinerCoinbase());
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_coinbase(): " + s);
        }
    }

    public boolean eth_mining() {
        Boolean s = null;
        try {
            return s = blockMiner.isMining();
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_mining(): " + s);
        }
    }


    public String eth_hashrate() {
        String s = null;
        try {
            return s = null;
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_hashrate(): " + s);
        }
    }

    public String eth_gasPrice(){
        String s = null;
        try {
            return s = TypeConverter.toJsonHex(eth.getGasPrice());
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_gasPrice(): " + s);
        }
    }

    public String[] eth_accounts() {
        String[] s = null;
        try {
            return s = personal_listAccounts();
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_accounts(): " + Arrays.toString(s));
        }
    }

    public String eth_blockNumber(){
        String s = null;
        try {
            Block bestBlock = blockchain.getBestBlock();
            long b = 0;
            if (bestBlock != null) {
                b = bestBlock.getNumber();
            }
            return s = TypeConverter.toJsonHex(b);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_blockNumber(): " + s);
        }
    }


    public String eth_getBalance(String address, String blockId) throws Exception {
        String s = null;
        try {
            byte[] addressAsByteArray = TypeConverter.StringHexToByteArray(address);
            BigInteger balance = getRepoByJsonBlockId(blockId).getBalance(addressAsByteArray);
            return s = TypeConverter.toJsonHex(balance);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getBalance(" + address + ", " + blockId + "): " + s);
        }
    }

    public String eth_getBalance(String address) throws Exception {
        String s = null;
        try {
            return s = eth_getBalance(address, "latest");
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getBalance(" + address + "): " + s);
        }
    }

    @Override
    public String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception {
        String s = null;
        try {
            byte[] addressAsByteArray = StringHexToByteArray(address);
            DataWord storageValue = getRepoByJsonBlockId(blockId).
                    getStorageValue(addressAsByteArray, new DataWord(StringHexToByteArray(storageIdx)));
            return s = TypeConverter.toJsonHex(storageValue.getData());
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getStorageAt(" + address + ", " + storageIdx + ", " + blockId + "): " + s);
        }
    }

    @Override
    public String eth_getTransactionCount(String address, String blockId) throws Exception {
        String s = null;
        try {
            byte[] addressAsByteArray = TypeConverter.StringHexToByteArray(address);
            BigInteger nonce = getRepoByJsonBlockId(blockId).getNonce(addressAsByteArray);
            return s = TypeConverter.toJsonHex(nonce);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getTransactionCount(" + address + ", " + blockId + "): " + s);
        }
    }

    public String eth_getBlockTransactionCountByHash(String blockHash) throws Exception {
        String s = null;
        try {
            Block b = getBlockByJSonHash(blockHash);
            if (b == null) return null;
            long n = b.getTransactionsList().size();
            return s = TypeConverter.toJsonHex(n);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getBlockTransactionCountByHash(" + blockHash + "): " + s);
        }
    }

    public String eth_getBlockTransactionCountByNumber(String bnOrId) throws Exception {
        String s = null;
        try {
            List<Transaction> list = getTransactionsByJsonBlockId(bnOrId);
            if (list == null) return null;
            long n = list.size();
            return s = TypeConverter.toJsonHex(n);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getBlockTransactionCountByNumber(" + bnOrId + "): " + s);
        }
    }

    public String eth_getUncleCountByBlockHash(String blockHash) throws Exception {
        String s = null;
        try {
            Block b = getBlockByJSonHash(blockHash);
            if (b == null) return null;
            long n = b.getUncleList().size();
            return s = TypeConverter.toJsonHex(n);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getUncleCountByBlockHash(" + blockHash + "): " + s);
        }
    }

    public String eth_getUncleCountByBlockNumber(String bnOrId) throws Exception {
        String s = null;
        try {
            Block b = getByJsonBlockId(bnOrId);
            if (b == null) return null;
            long n = b.getUncleList().size();
            return s = TypeConverter.toJsonHex(n);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getUncleCountByBlockNumber(" + bnOrId + "): " + s);
        }
    }

    public String eth_getCode(String address, String blockId) throws Exception {
        String s = null;
        try {
            byte[] addressAsByteArray = TypeConverter.StringHexToByteArray(address);
            byte[] code = getRepoByJsonBlockId(blockId).getCode(addressAsByteArray);
            return s = TypeConverter.toJsonHex(code);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getCode(" + address + ", " + blockId + "): " + s);
        }
    }

    public String eth_sign(String address, String data) throws Exception {
        String s = null;
        try {
            String ha = JSonHexToHex(address);
            Account account = getAccount(ha);

            if (account==null)
                throw new Exception("Inexistent account");

            // Todo: is not clear from the spec what hash function must be used to sign
            byte[] masgHash= HashUtil.sha3(TypeConverter.StringHexToByteArray(data));
            ECKey.ECDSASignature signature = account.getEcKey().sign(masgHash);
            // Todo: is not clear if result should be RlpEncoded or serialized by other means
            byte[] rlpSig = RLP.encode(signature);
            return s = TypeConverter.toJsonHex(rlpSig);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_sign(" + address + ", " + data + "): " + s);
        }
    }

    public String eth_sendTransaction(CallArguments args) throws Exception {

        String s = null;
        try {
            Account account = getAccount(JSonHexToHex(args.from));

            if (account == null)
                throw new Exception("From address private key could not be found in this node");

            if (args.data != null && args.data.startsWith("0x"))
                args.data = args.data.substring(2);

            Transaction tx = new Transaction(
                    args.nonce != null ? StringHexToByteArray(args.nonce) : bigIntegerToBytes(pendingState.getRepository().getNonce(account.getAddress())),
                    args.gasPrice != null ? StringHexToByteArray(args.gasPrice) : EMPTY_BYTE_ARRAY,
                    args.gasLimit != null ? StringHexToByteArray(args.gasLimit) : EMPTY_BYTE_ARRAY,
                    args.to != null ? StringHexToByteArray(args.to) : EMPTY_BYTE_ARRAY,
                    args.value != null ? StringHexToByteArray(args.value) : EMPTY_BYTE_ARRAY,
                    args.data != null ? StringHexToByteArray(args.data) : EMPTY_BYTE_ARRAY);
            tx.sign(account.getEcKey().getPrivKeyBytes());

            eth.submitTransaction(tx);

            return s = TypeConverter.toJsonHex(tx.getHash());
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_sendTransaction(" + args + "): " + s);
        }
    }

    public String eth_sendTransaction(String from, String to, String gas,
                                      String gasPrice, String value, String data, String nonce) throws Exception {
        String s = null;
        try {
            Transaction tx = new Transaction(
                    TypeConverter.StringHexToByteArray(nonce),
                    TypeConverter.StringHexToByteArray(gasPrice),
                    TypeConverter.StringHexToByteArray(gas),
                    TypeConverter.StringHexToByteArray(to), /*receiveAddress*/
                    TypeConverter.StringHexToByteArray(value),
                    TypeConverter.StringHexToByteArray(data));

            Account account = getAccount(from);
            if (account == null) throw new RuntimeException("No account " + from);

            tx.sign(account.getEcKey().getPrivKeyBytes());

            eth.submitTransaction(tx);

            return s = TypeConverter.toJsonHex(tx.getHash());
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_sendTransaction(" +
                    "from = [" + from + "], to = [" + to + "], gas = [" + gas + "], gasPrice = [" + gasPrice +
                    "], value = [" + value + "], data = [" + data + "], nonce = [" + nonce + "]" + "): " + s);
        }
    }

    public String eth_sendRawTransaction(String rawData) throws Exception {
        String s = null;
        try {
            Transaction tx = new Transaction(StringHexToByteArray(rawData));

            eth.submitTransaction(tx);

            return s = TypeConverter.toJsonHex(tx.getHash());
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_sendRawTransaction(" + rawData + "): " + s);
        }
    }

    public TransactionReceipt createCallTxAndExecute(CallArguments args, Block block) throws Exception {
        BinaryCallArguments bca = new BinaryCallArguments();
        bca.setArguments(args);
        Transaction tx = CallTransaction.createRawTransaction(0,
                bca.gasPrice,
                bca.gasLimit,
                bca.toAddress,
                bca.value,
                bca.data);

        return eth.callConstant(tx, block);
    }

    public String eth_call(CallArguments args, String bnOrId) throws Exception {

        String s = null;
        try {
            TransactionReceipt res = createCallTxAndExecute(args, getByJsonBlockId(bnOrId));
            return s = TypeConverter.toJsonHex(res.getExecutionResult());
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_call(" + args + "): " + s);
        }
    }

    public String eth_estimateGas(CallArguments args) throws Exception {
        String s = null;
        try {
            TransactionReceipt res = createCallTxAndExecute(args, blockchain.getBestBlock());
            return s = TypeConverter.toJsonHex(res.getGasUsed());
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_estimateGas(" + args + "): " + s);
        }
    }


    protected BlockResult getBlockResult(Block block, boolean fullTx) {
        if (block==null)
            return null;
        boolean isPending = ByteUtil.byteArrayToLong(block.getNonce()) == 0;
        BlockResult br = new BlockResult();
        br.number = isPending ? null : TypeConverter.toJsonHex(block.getNumber());
        br.hash = isPending ? null : TypeConverter.toJsonHex(block.getHash());
        br.parentHash = TypeConverter.toJsonHex(block.getParentHash());
        br.nonce = isPending ? null : TypeConverter.toJsonHex(block.getNonce());
        br.sha3Uncles= TypeConverter.toJsonHex(block.getUnclesHash());
        br.logsBloom = isPending ? null : TypeConverter.toJsonHex(block.getLogBloom());
        br.transactionsRoot = TypeConverter.toJsonHex(block.getTxTrieRoot());
        br.stateRoot = TypeConverter.toJsonHex(block.getStateRoot());
        br.receiptsRoot = TypeConverter.toJsonHex(block.getReceiptsRoot());
        br.miner = isPending ? null : TypeConverter.toJsonHex(block.getCoinbase());
        br.difficulty = TypeConverter.toJsonHex(block.getDifficulty());
        br.totalDifficulty = TypeConverter.toJsonHex(blockchain.getTotalDifficulty());
        if (block.getExtraData() != null)
            br.extraData = TypeConverter.toJsonHex(block.getExtraData());
        br.size = TypeConverter.toJsonHex(block.getEncoded().length);
        br.gasLimit = TypeConverter.toJsonHex(block.getGasLimit());
        br.gasUsed = TypeConverter.toJsonHex(block.getGasUsed());
        br.timestamp = TypeConverter.toJsonHex(block.getTimestamp());

        List<Object> txes = new ArrayList<>();
        if (fullTx) {
            for (int i = 0; i < block.getTransactionsList().size(); i++) {
                txes.add(new TransactionResultDTO(block, i, block.getTransactionsList().get(i)));
            }
        } else {
            for (Transaction tx : block.getTransactionsList()) {
                txes.add(toJsonHex(tx.getHash()));
            }
        }
        br.transactions = txes.toArray();

        List<String> ul = new ArrayList<>();
        for (BlockHeader header : block.getUncleList()) {
            ul.add(toJsonHex(header.getHash()));
        }
        br.uncles = ul.toArray(new String[ul.size()]);

        return br;
    }

    public BlockResult eth_getBlockByHash(String blockHash, Boolean fullTransactionObjects) throws Exception {
        BlockResult s = null;
        try {
            Block b = getBlockByJSonHash(blockHash);
            return getBlockResult(b, fullTransactionObjects);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getBlockByHash(" +  blockHash + ", " + fullTransactionObjects + "): " + s);
        }
    }

    public BlockResult eth_getBlockByNumber(String bnOrId, Boolean fullTransactionObjects) throws Exception {
        BlockResult s = null;
        try {
            Block b;
            if ("pending".equalsIgnoreCase(bnOrId)) {
                b = blockchain.createNewBlock(blockchain.getBestBlock(), pendingState.getPendingTransactions(), Collections.<BlockHeader>emptyList());
            } else {
                b = getByJsonBlockId(bnOrId);
            }
            return s = (b == null ? null : getBlockResult(b, fullTransactionObjects));
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getBlockByNumber(" +  bnOrId + ", " + fullTransactionObjects + "): " + s);
        }
    }

    public TransactionResultDTO eth_getTransactionByHash(String transactionHash) throws Exception {
        TransactionResultDTO s = null;
        try {
            TransactionInfo txInfo = blockchain.getTransactionInfo(StringHexToByteArray(transactionHash));
            if (txInfo == null) {
                return null;
            }
            Block block = blockchain.getBlockByHash(txInfo.getBlockHash());
            // need to return txes only from main chain
            Block mainBlock = blockchain.getBlockByNumber(block.getNumber());
            if (!Arrays.equals(block.getHash(), mainBlock.getHash())) {
                return null;
            }

            return s = new TransactionResultDTO(block, txInfo.getIndex(), block.getTransactionsList().get(txInfo.getIndex()));
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getTransactionByHash(" + transactionHash + "): " + s);
        }
    }

    public TransactionResultDTO eth_getTransactionByBlockHashAndIndex(String blockHash, String index) throws Exception {
        TransactionResultDTO s = null;
        try {
            Block b = getBlockByJSonHash(blockHash);
            if (b == null) return null;
            int idx = JSonHexToInt(index);
            if (idx >= b.getTransactionsList().size()) return null;
            Transaction tx = b.getTransactionsList().get(idx);
            return s = new TransactionResultDTO(b, idx, tx);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getTransactionByBlockHashAndIndex(" + blockHash + ", " + index + "): " + s);
        }
    }

    public TransactionResultDTO eth_getTransactionByBlockNumberAndIndex(String bnOrId, String index) throws Exception {
        TransactionResultDTO s = null;
        try {
            Block b = getByJsonBlockId(bnOrId);
            List<Transaction> txs = getTransactionsByJsonBlockId(bnOrId);
            if (txs == null) return null;
            int idx = JSonHexToInt(index);
            if (idx >= txs.size()) return null;
            Transaction tx = txs.get(idx);
            return s = new TransactionResultDTO(b, idx, tx);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getTransactionByBlockNumberAndIndex(" + bnOrId + ", " + index + "): " + s);
        }
    }

    public TransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception {
        TransactionReceiptDTO s = null;
        try {
            byte[] hash = TypeConverter.StringHexToByteArray(transactionHash);
            TransactionInfo txInfo = blockchain.getTransactionInfo(hash);

            if (txInfo == null)
                return null;

            Block block = blockchain.getBlockByHash(txInfo.getBlockHash());

            // need to return txes only from main chain
            Block mainBlock = blockchain.getBlockByNumber(block.getNumber());
            if (!Arrays.equals(block.getHash(), mainBlock.getHash())) {
                return null;
            }

            return s = new TransactionReceiptDTO(block, txInfo);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getTransactionReceipt(" + transactionHash + "): " + s);
        }
    }

    @Override
    public BlockResult eth_getUncleByBlockHashAndIndex(String blockHash, String uncleIdx) throws Exception {
        BlockResult s = null;
        try {
            Block block = blockchain.getBlockByHash(StringHexToByteArray(blockHash));
            if (block == null) return null;
            int idx = JSonHexToInt(uncleIdx);
            if (idx >= block.getUncleList().size()) return null;
            BlockHeader uncleHeader = block.getUncleList().get(idx);
            Block uncle = blockchain.getBlockByHash(uncleHeader.getHash());
            if (uncle == null) {
                uncle = new Block(uncleHeader, Collections.<Transaction>emptyList(), Collections.<BlockHeader>emptyList());
            }
            return s = getBlockResult(uncle, false);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getUncleByBlockHashAndIndex(" + blockHash + ", " + uncleIdx + "): " + s);
        }
    }

    @Override
    public BlockResult eth_getUncleByBlockNumberAndIndex(String blockId, String uncleIdx) throws Exception {
        BlockResult s = null;
        try {
            Block block = getByJsonBlockId(blockId);
            return s = block == null ? null :
                    eth_getUncleByBlockHashAndIndex(toJsonHex(block.getHash()), uncleIdx);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getUncleByBlockNumberAndIndex(" + blockId + ", " + uncleIdx + "): " + s);
        }
    }

    @Override
    public String[] eth_getCompilers() {
        String[] s = null;
        try {
            return s = new String[] {"solidity"};
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getCompilers(): " + Arrays.toString(s));
        }
    }

    @Override
    public CompilationResult eth_compileLLL(String contract) {
        throw new UnsupportedOperationException("LLL compiler not supported");
    }

    @Override
    public CompilationResult eth_compileSolidity(String contract) throws Exception {
        CompilationResult s = null;
        try {
            SolidityCompiler.Result res = SolidityCompiler.compile(
                    contract.getBytes(), true, SolidityCompiler.Options.ABI, SolidityCompiler.Options.BIN);
            if (!res.errors.isEmpty()) {
                throw new RuntimeException("Compilation error: " + res.errors);
            }
            org.ethereum.solidity.compiler.CompilationResult result = org.ethereum.solidity.compiler.CompilationResult.parse(res.output);
            CompilationResult ret = new CompilationResult();
            org.ethereum.solidity.compiler.CompilationResult.ContractMetadata contractMetadata = result.contracts.values().iterator().next();
            ret.code = toJsonHex(contractMetadata.bin);
            ret.info = new CompilationInfo();
            ret.info.source = contract;
            ret.info.language = "Solidity";
            ret.info.languageVersion = "0";
            ret.info.compilerVersion = result.version;
            ret.info.abiDefinition = new CallTransaction.Contract(contractMetadata.abi).functions;
            return s = ret;
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_compileSolidity(" + contract + ")" + s);
        }
    }

    @Override
    public CompilationResult eth_compileSerpent(String contract){
        throw new UnsupportedOperationException("Serpent compiler not supported");
    }

    @Override
    public String eth_resend() {
        throw new UnsupportedOperationException("JSON RPC method eth_resend not implemented yet");
    }

    @Override
    public String eth_pendingTransactions() {
        throw new UnsupportedOperationException("JSON RPC method eth_pendingTransactions not implemented yet");
    }

    static class Filter {
        static final int MAX_EVENT_COUNT = 1024; // prevent OOM when Filers are forgotten
        static abstract class FilterEvent {
            public abstract Object getJsonEventObject();
        }
        List<FilterEvent> events = new LinkedList<>();

        public synchronized boolean hasNew() { return !events.isEmpty();}

        public synchronized Object[] poll() {
            Object[] ret = new Object[events.size()];
            for (int i = 0; i < ret.length; i++) {
                ret[i] = events.get(i).getJsonEventObject();
            }
            this.events.clear();
            return ret;
        }

        protected synchronized void add(FilterEvent evt) {
            events.add(evt);
            if (events.size() > MAX_EVENT_COUNT) events.remove(0);
        }

        public void newBlockReceived(Block b) {}
        public void newPendingTx(Transaction tx) {}
    }

    static class NewBlockFilter extends Filter {
        class NewBlockFilterEvent extends FilterEvent {
            public final Block b;
            NewBlockFilterEvent(Block b) {this.b = b;}

            @Override
            public String getJsonEventObject() {
                return toJsonHex(b.getHash());
            }
        }

        public void newBlockReceived(Block b) {
            add(new NewBlockFilterEvent(b));
        }
    }

    static class PendingTransactionFilter extends Filter {
        class PendingTransactionFilterEvent extends FilterEvent {
            private final Transaction tx;

            PendingTransactionFilterEvent(Transaction tx) {this.tx = tx;}

            @Override
            public String getJsonEventObject() {
                return toJsonHex(tx.getHash());
            }
        }

        public void newPendingTx(Transaction tx) {
            add(new PendingTransactionFilterEvent(tx));
        }
    }

    class JsonLogFilter extends Filter {
        class LogFilterEvent extends FilterEvent {
            private final LogFilterElement el;

            LogFilterEvent(LogFilterElement el) {
                this.el = el;
            }

            @Override
            public LogFilterElement getJsonEventObject() {
                return el;
            }
        }

        LogFilter logFilter;
        boolean onNewBlock;
        boolean onPendingTx;

        public JsonLogFilter(LogFilter logFilter) {
            this.logFilter = logFilter;
        }

        void onLogMatch(LogInfo logInfo, Block b, int txIndex, Transaction tx, int logIdx) {
            add(new LogFilterEvent(new LogFilterElement(logInfo, b, txIndex, tx, logIdx)));
        }

        void onTransactionReceipt(TransactionReceipt receipt, Block b, int txIndex) {
            if (logFilter.matchBloom(receipt.getBloomFilter())) {
                int logIdx = 0;
                for (LogInfo logInfo : receipt.getLogInfoList()) {
                    if (logFilter.matchBloom(logInfo.getBloom()) && logFilter.matchesExactly(logInfo)) {
                        onLogMatch(logInfo, b, txIndex, receipt.getTransaction(), logIdx);
                    }
                    logIdx++;
                }
            }
        }

        void onTransaction(Transaction tx, Block b, int txIndex) {
            if (logFilter.matchesContractAddress(tx.getReceiveAddress())) {
                TransactionInfo txInfo = blockchain.getTransactionInfo(tx.getHash());
                onTransactionReceipt(txInfo.getReceipt(), b, txIndex);
            }
        }

        void onBlock(Block b) {
            if (logFilter.matchBloom(new Bloom(b.getLogBloom()))) {
                int txIdx = 0;
                for (Transaction tx : b.getTransactionsList()) {
                    onTransaction(tx, b, txIdx);
                    txIdx++;
                }
            }
        }

        @Override
        public void newBlockReceived(Block b) {
            if (onNewBlock) onBlock(b);
        }

        @Override
        public void newPendingTx(Transaction tx) {
            // TODO add TransactionReceipt for PendingTx
//            if (onPendingTx)
        }
    }

    @Override
    public String eth_newFilter(FilterRequest fr) throws Exception {
        String str = null;
        try {
            LogFilter logFilter = new LogFilter();

            if (fr.address instanceof String) {
                logFilter.withContractAddress(StringHexToByteArray((String) fr.address));
            } else if (fr.address instanceof String[]) {
                List<byte[]> addr = new ArrayList<>();
                for (String s : ((String[]) fr.address)) {
                    addr.add(StringHexToByteArray(s));
                }
                logFilter.withContractAddress(addr.toArray(new byte[0][]));
            }

            if (fr.topics != null) {
                for (Object topic : fr.topics) {
                    if (topic == null) {
                        logFilter.withTopic();
                    } else if (topic instanceof String) {
                        logFilter.withTopic(new DataWord(StringHexToByteArray((String) topic)).getData());
                    } else if (topic instanceof String[]) {
                        List<byte[]> t = new ArrayList<>();
                        for (String s : ((String[]) topic)) {
                            t.add(new DataWord(StringHexToByteArray(s)).getData());
                        }
                        logFilter.withTopic(t.toArray(new byte[0][]));
                    }
                }
            }

            JsonLogFilter filter = new JsonLogFilter(logFilter);
            int id = filterCounter.getAndIncrement();
            installedFilters.put(id, filter);

            Block blockFrom = fr.fromBlock == null ? null : getByJsonBlockId(fr.fromBlock);
            Block blockTo = fr.toBlock == null ? null : getByJsonBlockId(fr.toBlock);

            if (blockFrom != null) {
                // need to add historical data
                blockTo = blockTo == null ? blockchain.getBestBlock() : blockTo;
                for (long blockNum = blockFrom.getNumber(); blockNum <= blockTo.getNumber(); blockNum++) {
                    filter.onBlock(blockchain.getBlockByNumber(blockNum));
                }
            }

            // the following is not precisely documented
            if ("pending".equalsIgnoreCase(fr.fromBlock) || "pending".equalsIgnoreCase(fr.toBlock)) {
                filter.onPendingTx = true;
            } else if ("latest".equalsIgnoreCase(fr.fromBlock) || "latest".equalsIgnoreCase(fr.toBlock)) {
                filter.onNewBlock = true;
            }

            return str = toJsonHex(id);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_newFilter(" + fr + "): " + str);
        }
    }

    @Override
    public String eth_newBlockFilter() {
        String s = null;
        try {
            int id = filterCounter.getAndIncrement();
            installedFilters.put(id, new NewBlockFilter());
            return s = toJsonHex(id);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_newBlockFilter(): " + s);
        }
    }

    @Override
    public String eth_newPendingTransactionFilter() {
        String s = null;
        try {
            int id = filterCounter.getAndIncrement();
            installedFilters.put(id, new PendingTransactionFilter());
            return s = toJsonHex(id);
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_newPendingTransactionFilter(): " + s);
        }
    }

    @Override
    public boolean eth_uninstallFilter(String filterId) {
        Boolean s = null;
        try {
            if (filterId == null) return false;
            return s = installedFilters.remove(StringHexToBigInteger(filterId).intValue()) != null;
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_uninstallFilter(" + filterId + "): " + s);
        }
    }

    @Override
    public Object[] eth_getFilterChanges(String filterId) {
        Object[] s = null;
        try {
            Filter filter = installedFilters.get(StringHexToBigInteger(filterId).intValue());
            if (filter == null) return null;
            return s = filter.poll();
        } finally {
            if (log.isDebugEnabled()) log.debug("eth_getFilterChanges(" + filterId + "): " + Arrays.toString(s));
        }
    }

    @Override
    public Object[] eth_getFilterLogs(String filterId) {
        log.debug("eth_getFilterLogs ...");
        return eth_getFilterChanges(filterId);
    }

    @Override
    public Object[] eth_getLogs(FilterRequest filterRequest) throws Exception {
        log.debug("eth_getLogs ...");
        String id = eth_newFilter(filterRequest);
        Object[] ret = eth_getFilterChanges(id);
        eth_uninstallFilter(id);
        return ret;
    }

    @Override
    public String eth_getWork() {
        throw new UnsupportedOperationException("JSON RPC method eth_getWork not implemented yet");
    }

    @Override
    public String eth_submitWork() {
        throw new UnsupportedOperationException("JSON RPC method eth_submitWork not implemented yet");
    }

    @Override
    public String eth_submitHashrate() {
        throw new UnsupportedOperationException("JSON RPC method eth_submitHashrate not implemented yet");
    }

    @Override
    public String db_putString() {
        throw new UnsupportedOperationException("JSON RPC method db_putString not implemented yet");
    }

    @Override
    public String db_getString() {
        throw new UnsupportedOperationException("JSON RPC method db_getString not implemented yet");
    }

    @Override
    public String db_putHex() {
        throw new UnsupportedOperationException("JSON RPC method db_putHex not implemented yet");
    }

    @Override
    public String db_getHex() {
        throw new UnsupportedOperationException("JSON RPC method db_getHex not implemented yet");
    }

    @Override
    public String shh_post() {
        throw new UnsupportedOperationException("JSON RPC method shh_post not implemented yet");
    }

    @Override
    public String shh_version() {
        throw new UnsupportedOperationException("JSON RPC method shh_version not implemented yet");
    }

    @Override
    public String shh_newIdentity() {
        throw new UnsupportedOperationException("JSON RPC method shh_newIdentity not implemented yet");
    }

    @Override
    public String shh_hasIdentity() {
        throw new UnsupportedOperationException("JSON RPC method shh_hasIdentity not implemented yet");
    }

    @Override
    public String shh_newGroup() {
        throw new UnsupportedOperationException("JSON RPC method shh_newGroup not implemented yet");
    }

    @Override
    public String shh_addToGroup() {
        throw new UnsupportedOperationException("JSON RPC method shh_addToGroup not implemented yet");
    }

    @Override
    public String shh_newFilter() {
        throw new UnsupportedOperationException("JSON RPC method shh_newFilter not implemented yet");
    }

    @Override
    public String shh_uninstallFilter() {
        throw new UnsupportedOperationException("JSON RPC method shh_uninstallFilter not implemented yet");
    }

    @Override
    public String shh_getFilterChanges() {
        throw new UnsupportedOperationException("JSON RPC method shh_getFilterChanges not implemented yet");
    }

    @Override
    public String shh_getMessages() {
        throw new UnsupportedOperationException("JSON RPC method shh_getMessages not implemented yet");
    }

    @Override
    public boolean admin_addPeer(String enodeUrl) {
        eth.connect(new Node(enodeUrl));
        return true;
    }

    @Override
    public String admin_exportChain() {
        throw new UnsupportedOperationException("JSON RPC method admin_exportChain not implemented yet");
    }

    @Override
    public String admin_importChain() {
        throw new UnsupportedOperationException("JSON RPC method admin_importChain not implemented yet");
    }

    @Override
    public String admin_sleepBlocks() {
        throw new UnsupportedOperationException("JSON RPC method admin_sleepBlocks not implemented yet");
    }

    @Override
    public String admin_verbosity() {
        throw new UnsupportedOperationException("JSON RPC method admin_verbosity not implemented yet");
    }

    @Override
    public String admin_setSolc() {
        throw new UnsupportedOperationException("JSON RPC method admin_setSolc not implemented yet");
    }

    @Override
    public String admin_startRPC() {
        throw new UnsupportedOperationException("JSON RPC method admin_startRPC not implemented yet");
    }

    @Override
    public String admin_stopRPC() {
        throw new UnsupportedOperationException("JSON RPC method admin_stopRPC not implemented yet");
    }

    @Override
    public String admin_setGlobalRegistrar() {
        throw new UnsupportedOperationException("JSON RPC method admin_setGlobalRegistrar not implemented yet");
    }

    @Override
    public String admin_setHashReg() {
        throw new UnsupportedOperationException("JSON RPC method admin_setHashReg not implemented yet");
    }

    @Override
    public String admin_setUrlHint() {
        throw new UnsupportedOperationException("JSON RPC method admin_setUrlHint not implemented yet");
    }

    @Override
    public String admin_saveInfo() {
        throw new UnsupportedOperationException("JSON RPC method admin_saveInfo not implemented yet");
    }

    @Override
    public String admin_register() {
        throw new UnsupportedOperationException("JSON RPC method admin_register not implemented yet");
    }

    @Override
    public String admin_registerUrl() {
        throw new UnsupportedOperationException("JSON RPC method admin_registerUrl not implemented yet");
    }

    @Override
    public String admin_startNatSpec() {
        throw new UnsupportedOperationException("JSON RPC method admin_startNatSpec not implemented yet");
    }

    @Override
    public String admin_stopNatSpec() {
        throw new UnsupportedOperationException("JSON RPC method admin_stopNatSpec not implemented yet");
    }

    @Override
    public String admin_getContractInfo() {
        throw new UnsupportedOperationException("JSON RPC method admin_getContractInfo not implemented yet");
    }

    @Override
    public String admin_httpGet() {
        throw new UnsupportedOperationException("JSON RPC method admin_httpGet not implemented yet");
    }

    @Override
    public String admin_nodeInfo() {
        throw new UnsupportedOperationException("JSON RPC method admin_nodeInfo not implemented yet");
    }

    @Override
    public String admin_peers() {
        throw new UnsupportedOperationException("JSON RPC method admin_peers not implemented yet");
    }

    @Override
    public String admin_datadir() {
        throw new UnsupportedOperationException("JSON RPC method admin_datadir not implemented yet");
    }

    @Override
    public String net_addPeer() {
        throw new UnsupportedOperationException("JSON RPC method net_addPeer not implemented yet");
    }

    @Override
    public boolean miner_start() {
        blockMiner.startMining();
        return true;
    }

    @Override
    public boolean miner_stop() {
        blockMiner.stopMining();
        return true;
    }

    @Override
    public boolean miner_setEtherbase(String coinBase) throws Exception {
        blockchain.setMinerCoinbase(TypeConverter.StringHexToByteArray(coinBase));
        return true;
    }

    @Override
    public boolean miner_setExtra(String data) throws Exception {
        blockchain.setMinerExtraData(TypeConverter.StringHexToByteArray(data));
        return true;
    }

    @Override
    public boolean miner_setGasPrice(String newMinGasPrice) {
        blockMiner.setMinGasPrice(TypeConverter.StringHexToBigInteger(newMinGasPrice));
        return true;
    }

    @Override
    public boolean miner_startAutoDAG() {
        return false;
    }

    @Override
    public boolean miner_stopAutoDAG() {
        return false;
    }

    @Override
    public boolean miner_makeDAG() {
        return false;
    }

    @Override
    public String miner_hashrate() {
        return "0x01";
    }

    @Override
    public String debug_printBlock() {
        throw new UnsupportedOperationException("JSON RPC method debug_printBlock not implemented yet");
    }

    @Override
    public String debug_getBlockRlp() {
        throw new UnsupportedOperationException("JSON RPC method debug_getBlockRlp not implemented yet");
    }

    @Override
    public String debug_setHead() {
        throw new UnsupportedOperationException("JSON RPC method debug_setHead not implemented yet");
    }

    @Override
    public String debug_processBlock() {
        throw new UnsupportedOperationException("JSON RPC method debug_processBlock not implemented yet");
    }

    @Override
    public String debug_seedHash() {
        throw new UnsupportedOperationException("JSON RPC method debug_seedHash not implemented yet");
    }

    @Override
    public String debug_dumpBlock() {
        throw new UnsupportedOperationException("JSON RPC method debug_dumpBlock not implemented yet");
    }

    @Override
    public String debug_metrics() {
        throw new UnsupportedOperationException("JSON RPC method debug_metrics not implemented yet");
    }

    @Override
    public String personal_newAccount(String password) {
        String s = null;
        try {
            Account account = addAccount(password);
            return s = toJsonHex(account.getAddress());
        } finally {
            if (log.isDebugEnabled()) log.debug("personal_newAccount(*****): " + s);
        }
    }

    public String personal_importRawKey(String keydata, String passphrase) {
        String s = null;
        try {

        } finally {
        }
        return s;
    }

    @Override
    public boolean personal_unlockAccount(String addr, String pass, String duration) {
        String s = null;
        try {
            return true;
        } finally {
            if (log.isDebugEnabled()) log.debug("personal_unlockAccount(" + addr + ", ***, " + duration + "): " + s);
        }
    }

    @Override
    public String[] personal_listAccounts() {
        return keystoreManager.listStoredKeys();
//        String[] ret = new String[accounts.size()];
//        try {
//            int i = 0;
//            for (ByteArrayWrapper addr : accounts.keySet()) {
//                ret[i++] = toJsonHex(addr.getData());
//            }
//            return ret;
//        } finally {
//            if (log.isDebugEnabled()) log.debug("personal_listAccounts(): " + Arrays.toString(ret));
//        }
    }

    /**
     * List method names for client side terminal competition.
     * Forms strings list in format: `List("methodName arg1 arg2", "methodName2")`
     */
    @Override
    public String[] listAvailableMethods() {
        Set<String> ignore = Arrays.asList(Object.class.getMethods())
                .stream()
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        return Arrays.asList(JsonRpcImpl.class.getMethods())
                .stream()
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !ignore.contains(method.getName()))
                .map(method ->
                    method.getName() + " " + Arrays.asList(method.getParameters())
                            .stream()
                            .map(parameter ->
                                    parameter.isNamePresent() ? parameter.getName() : parameter.getType().getSimpleName())
                            .collect(Collectors.joining(" "))
                )
                .toArray(size -> new String[size]);
    }
}
