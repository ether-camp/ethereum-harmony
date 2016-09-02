package com.ethercamp.harmony.jsonrpc;

import com.ethercamp.harmony.keystore.Keystore;
import com.ethercamp.harmony.service.BlockchainInfoService;
import com.ethercamp.harmony.util.ErrorCodes;
import com.ethercamp.harmony.util.HarmonyException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.core.TransactionInfo;
import org.ethereum.db.TransactionStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.manager.WorldManager;
import org.ethereum.mine.BlockMiner;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.sync.SyncManager;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.LRUMap;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.ethercamp.harmony.jsonrpc.TypeConverter.*;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ethereum.util.ByteUtil.bigIntegerToBytes;

/**
 * @author Anton Nashatyrev
 */
@Slf4j(topic = "jsonrpc")
// cant put @Component definition as this class will conflict with class in core
// wait for core class to be removed
public class JsonRpcImpl implements JsonRpc {

    private static final String BLOCK_LATEST = "latest";

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
            if (args.gas != null && args.gas.length()!=0)
                gasLimit = JSonHexToLong(args.gas);

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
    Keystore keystore;

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

    @Autowired
    SystemProperties config;

    @Autowired
    ConfigCapabilities configCapabilities;

    /**
     * Lowercase hex address as a key.
     */
    Map<String, Account> unlockedAccounts = new ConcurrentHashMap<>();

    /**
     * State fields
     */
    protected volatile long initialBlockNumber;

    AtomicInteger filterCounter = new AtomicInteger(1);
    Map<Integer, Filter> installedFilters = new Hashtable<>();
    Map<ByteArrayWrapper, TransactionReceipt> pendingReceipts = Collections.synchronizedMap(new LRUMap<>(0, 1024));

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

            @Override
            public void onPendingTransactionUpdate(TransactionReceipt txReceipt, PendingTransactionState state, Block block) {
                ByteArrayWrapper txHashW = new ByteArrayWrapper(txReceipt.getTransaction().getHash());
                if (state.isPending() || state == PendingTransactionState.DROPPED) {
                    pendingReceipts.put(txHashW, txReceipt);
                } else {
                    pendingReceipts.remove(txHashW);
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

    private String JSonHexToHex(String x) {
        if (!x.startsWith("0x"))
            throw new RuntimeException("Incorrect hex syntax");
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

    /**
     * @param address
     * @return unlocked account with private key ready for signing tx
     * @throws RuntimeException if account not unlocked or not found in keystore
     */
    protected Account getAccountFromKeystore(String address) throws RuntimeException {
        if (address.indexOf("0x") == 0) {
            address = address.substring(2);
        }
        final Account account = unlockedAccounts.get(address.toLowerCase());
        if (account != null) {
            return account;
        }

        if (keystore.hasStoredKey(address)) {
            throw new HarmonyException("Unlocked account is required", ErrorCodes.ERROR__101_UNLOCK_ACCOUNT);
        } else {
            throw new HarmonyException("Key not found in keystore", ErrorCodes.ERROR__102_KEY_NOT_FOUND);
        }
    }

    protected Account importAccount(ECKey key, String password) {
        final Account account = new Account();
        account.init(key);

        keystore.storeKey(key, password);
        return account;
    }

    public String web3_clientVersion() {
        return "EthereumJ" + "/v" + config.projectVersion() + "/" +
                System.getProperty("os.name") + "/Java1.7/" + config.projectVersionModifier() + "-" + BuildInfo.buildHash;
    }

    public String web3_sha3(String data) throws Exception {
        byte[] result = HashUtil.sha3(data.getBytes());
        return TypeConverter.toJsonHex(result);
    }

    public String net_version() {
        return eth_protocolVersion();
    }

    public String net_peerCount(){
        int size = channelManager.getActivePeers().size();
        return TypeConverter.toJsonHex(size);
    }

    public boolean net_listening() {
        return peerServer.isListening();
    }

    public String eth_protocolVersion(){
        return configCapabilities.getConfigCapabilities().stream()
                .filter(p -> p.isEth())
                .map(p -> ((Byte) p.getVersion()).intValue())
                .reduce(0, (state, v) -> Math.max(state, v))
                .toString();
    }

    public SyncingResult eth_syncing(){
        return new SyncingResult(
                TypeConverter.toJsonHex(initialBlockNumber),
                TypeConverter.toJsonHex(blockchain.getBestBlock().getNumber()),
                TypeConverter.toJsonHex(syncManager.getLastKnownBlockNumber())
        );
    }

    public String eth_coinbase() {
        return toJsonHex(blockchain.getMinerCoinbase());
    }

    public boolean eth_mining() {
        return blockMiner.isMining();
    }


//    public String eth_hashrate() {
//        String s = null;
//        try {
//            return s = null;
//        } finally {
//            if (log.isDebugEnabled()) log.debug("eth_hashrate(): " + s);
//        }
//    }

    public String eth_gasPrice(){
        return TypeConverter.toJsonHex(eth.getGasPrice());
    }

    public String[] eth_accounts() {
        return personal_listAccounts();
    }

    public String eth_blockNumber() {
        return TypeConverter.toJsonHex(blockchain.getBestBlock().getNumber());
    }

    public String eth_getBalance(String address, String blockId) throws Exception {
        Objects.requireNonNull(address, "address is required");
        blockId = blockId == null ? BLOCK_LATEST : blockId;

        byte[] addressAsByteArray = TypeConverter.StringHexToByteArray(address);
        BigInteger balance = getRepoByJsonBlockId(blockId).getBalance(addressAsByteArray);
        return TypeConverter.toJsonHex(balance);
    }

    public String eth_getLastBalance(String address) throws Exception {
        return eth_getBalance(address, BLOCK_LATEST);
    }

    @Override
    public String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception {
        byte[] addressAsByteArray = StringHexToByteArray(address);
        DataWord storageValue = getRepoByJsonBlockId(blockId).
                getStorageValue(addressAsByteArray, new DataWord(StringHexToByteArray(storageIdx)));
        return TypeConverter.toJsonHex(storageValue.getData());
    }

    @Override
    public String eth_getTransactionCount(String address, String blockId) throws Exception {
        byte[] addressAsByteArray = TypeConverter.StringHexToByteArray(address);
        BigInteger nonce = getRepoByJsonBlockId(blockId).getNonce(addressAsByteArray);
        return TypeConverter.toJsonHex(nonce);
    }

    public String eth_getBlockTransactionCountByHash(String blockHash) throws Exception {
        Block b = getBlockByJSonHash(blockHash);
        if (b == null) return null;
        long n = b.getTransactionsList().size();
        return TypeConverter.toJsonHex(n);
    }

    public String eth_getBlockTransactionCountByNumber(String bnOrId) throws Exception {
        List<Transaction> list = getTransactionsByJsonBlockId(bnOrId);
        if (list == null) return null;
        long n = list.size();
        return TypeConverter.toJsonHex(n);
    }

    public String eth_getUncleCountByBlockHash(String blockHash) throws Exception {
        Block b = getBlockByJSonHash(blockHash);
        if (b == null) return null;
        long n = b.getUncleList().size();
        return TypeConverter.toJsonHex(n);
    }

    public String eth_getUncleCountByBlockNumber(String bnOrId) throws Exception {
        Block b = getByJsonBlockId(bnOrId);
        if (b == null) return null;
        long n = b.getUncleList().size();
        return TypeConverter.toJsonHex(n);
    }

    public String eth_getCode(String address, String blockId) throws Exception {
        byte[] addressAsByteArray = TypeConverter.StringHexToByteArray(address);
        byte[] code = getRepoByJsonBlockId(blockId).getCode(addressAsByteArray);
        return TypeConverter.toJsonHex(code);
    }

    public String eth_sign(String address, String data) throws Exception {
        String ha = JSonHexToHex(address);
        Account account = getAccountFromKeystore(ha);

        if (account==null)
            throw new Exception("Inexistent account");

        // Todo: is not clear from the spec what hash function must be used to sign
        byte[] masgHash= HashUtil.sha3(TypeConverter.StringHexToByteArray(data));
        ECKey.ECDSASignature signature = account.getEcKey().sign(masgHash);
        // Todo: is not clear if result should be RlpEncoded or serialized by other means
        byte[] rlpSig = RLP.encode(signature);
        return TypeConverter.toJsonHex(rlpSig);
    }

    public String eth_sendTransaction(CallArguments args) throws Exception {
        Account account = getAccountFromKeystore(JSonHexToHex(args.from));

        if (args.data != null && args.data.startsWith("0x"))
            args.data = args.data.substring(2);

        Transaction tx = new Transaction(
                args.nonce != null ? StringHexToByteArray(args.nonce) : bigIntegerToBytes(pendingState.getRepository().getNonce(account.getAddress())),
                args.gasPrice != null ? StringHexToByteArray(args.gasPrice) : ByteUtil.longToBytesNoLeadZeroes(eth.getGasPrice()),
                args.gas != null ? StringHexToByteArray(args.gas) : ByteUtil.longToBytes(90_000),
                args.to != null ? StringHexToByteArray(args.to) : EMPTY_BYTE_ARRAY,
                args.value != null ? StringHexToByteArray(args.value) : EMPTY_BYTE_ARRAY,
                args.data != null ? StringHexToByteArray(args.data) : EMPTY_BYTE_ARRAY);
        tx.sign(account.getEcKey());

        eth.submitTransaction(tx);

        return TypeConverter.toJsonHex(tx.getHash());
    }

    public String eth_sendTransactionArgs(String from, String to, String gas,
                                      String gasPrice, String value, String data, String nonce) throws Exception {
        Transaction tx = new Transaction(
                TypeConverter.StringHexToByteArray(nonce),
                TypeConverter.StringHexToByteArray(gasPrice),
                TypeConverter.StringHexToByteArray(gas),
                TypeConverter.StringHexToByteArray(to), /*receiveAddress*/
                TypeConverter.StringHexToByteArray(value),
                TypeConverter.StringHexToByteArray(data));

        Account account = getAccountFromKeystore(from);

        tx.sign(account.getEcKey());

        eth.submitTransaction(tx);

        return TypeConverter.toJsonHex(tx.getHash());
    }

    public String eth_sendRawTransaction(String rawData) throws Exception {
        Transaction tx = new Transaction(StringHexToByteArray(rawData));

        tx.rlpParse();
        eth.submitTransaction(tx);

        return TypeConverter.toJsonHex(tx.getHash());
    }

    protected TransactionReceipt createCallTxAndExecute(CallArguments args, Block block) throws Exception {
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
        TransactionReceipt res = createCallTxAndExecute(args, getByJsonBlockId(bnOrId));
        return TypeConverter.toJsonHex(res.getExecutionResult());
    }

    public String eth_estimateGas(CallArguments args) throws Exception {
        TransactionReceipt res = createCallTxAndExecute(args, blockchain.getBestBlock());
        return TypeConverter.toJsonHex(res.getGasUsed());
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

    public BlockResult eth_getBlockByHash(String blockHash,Boolean fullTransactionObjects) throws Exception {
        final Block b = getBlockByJSonHash(blockHash);
        return getBlockResult(b, fullTransactionObjects);
    }

    public BlockResult eth_getBlockByNumber(String bnOrId,Boolean fullTransactionObjects) throws Exception {
        final Block b;
        if ("pending".equalsIgnoreCase(bnOrId)) {
            b = blockchain.createNewBlock(blockchain.getBestBlock(), pendingState.getPendingTransactions(), Collections.<BlockHeader>emptyList());
        } else {
            b = getByJsonBlockId(bnOrId);
        }
        return (b == null ? null : getBlockResult(b, fullTransactionObjects));
    }

    public TransactionResultDTO eth_getTransactionByHash(String transactionHash) throws Exception {
        byte[] txHash = StringHexToByteArray(transactionHash);
        Block block = null;

        TransactionInfo txInfo = blockchain.getTransactionInfo(txHash);

        if (txInfo == null) {
            TransactionReceipt receipt = pendingReceipts.get(new ByteArrayWrapper(txHash));

            if (receipt == null) {
                return null;
            }
            txInfo = new TransactionInfo(receipt);
        } else {
            block = blockchain.getBlockByHash(txInfo.getBlockHash());
            // need to return txes only from main chain
            Block mainBlock = blockchain.getBlockByNumber(block.getNumber());
            if (!Arrays.equals(block.getHash(), mainBlock.getHash())) {
                return null;
            }
            txInfo.setTransaction(block.getTransactionsList().get(txInfo.getIndex()));
        }

        return new TransactionResultDTO(block, txInfo.getIndex(), txInfo.getReceipt().getTransaction());
    }

    public TransactionResultDTO eth_getTransactionByBlockHashAndIndex(String blockHash,String index) throws Exception {
        Block b = getBlockByJSonHash(blockHash);
        if (b == null) return null;
        int idx = JSonHexToInt(index);
        if (idx >= b.getTransactionsList().size()) return null;
        Transaction tx = b.getTransactionsList().get(idx);
        return new TransactionResultDTO(b, idx, tx);
    }

    public TransactionResultDTO eth_getTransactionByBlockNumberAndIndex(String bnOrId, String index) throws Exception {
        Block b = getByJsonBlockId(bnOrId);
        List<Transaction> txs = getTransactionsByJsonBlockId(bnOrId);
        if (txs == null) return null;
        int idx = JSonHexToInt(index);
        if (idx >= txs.size()) return null;
        Transaction tx = txs.get(idx);
        return new TransactionResultDTO(b, idx, tx);
    }

    public TransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception {
        byte[] hash = TypeConverter.StringHexToByteArray(transactionHash);

        TransactionReceipt pendingReceipt = pendingReceipts.get(new ByteArrayWrapper(hash));

        TransactionInfo txInfo;
        Block block;

        if (pendingReceipt != null) {
            txInfo = new TransactionInfo(pendingReceipt);
            block = null;
        } else {
            txInfo = blockchain.getTransactionInfo(hash);

            if (txInfo == null)
                return null;

            block = blockchain.getBlockByHash(txInfo.getBlockHash());

            // need to return txes only from main chain
            Block mainBlock = blockchain.getBlockByNumber(block.getNumber());
            if (!Arrays.equals(block.getHash(), mainBlock.getHash())) {
                return null;
            }
        }

        return new TransactionReceiptDTO(block, txInfo);
    }

    @Override
    public TransactionReceiptDTOExt ethj_getTransactionReceipt(String transactionHash) throws Exception {
        byte[] hash = TypeConverter.StringHexToByteArray(transactionHash);

        TransactionReceipt pendingReceipt = pendingReceipts.get(new ByteArrayWrapper(hash));

        TransactionInfo txInfo;
        Block block;

        if (pendingReceipt != null) {
            txInfo = new TransactionInfo(pendingReceipt);
            block = null;
        } else {
            txInfo = blockchain.getTransactionInfo(hash);

            if (txInfo == null)
                return null;

            block = blockchain.getBlockByHash(txInfo.getBlockHash());

            // need to return txes only from main chain
            Block mainBlock = blockchain.getBlockByNumber(block.getNumber());
            if (!Arrays.equals(block.getHash(), mainBlock.getHash())) {
                return null;
            }
        }

        return new TransactionReceiptDTOExt(block, txInfo);
    }

    @Override
    public BlockResult eth_getUncleByBlockHashAndIndex(String blockHash, String uncleIdx) throws Exception {
        Block block = blockchain.getBlockByHash(StringHexToByteArray(blockHash));
        if (block == null) return null;
        int idx = JSonHexToInt(uncleIdx);
        if (idx >= block.getUncleList().size()) return null;
        BlockHeader uncleHeader = block.getUncleList().get(idx);
        Block uncle = blockchain.getBlockByHash(uncleHeader.getHash());
        if (uncle == null) {
            uncle = new Block(uncleHeader, Collections.<Transaction>emptyList(), Collections.<BlockHeader>emptyList());
        }
        return getBlockResult(uncle, false);
    }

    @Override
    public BlockResult eth_getUncleByBlockNumberAndIndex(String blockId, String uncleIdx) throws Exception {
        Block block = getByJsonBlockId(blockId);
        return block == null ? null :
                eth_getUncleByBlockHashAndIndex(toJsonHex(block.getHash()), uncleIdx);
    }

    @Override
    public String[] eth_getCompilers() {
        return new String[] {"solidity"};
    }

//    @Override
//    public CompilationResult eth_compileLLL(String contract) {
//        throw new UnsupportedOperationException("LLL compiler not supported");
//    }

    @Override
    public CompilationResult eth_compileSolidity(String contract) throws Exception {
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
        return ret;
    }

//    @Override
//    public CompilationResult eth_compileSerpent(String contract){
//        throw new UnsupportedOperationException("Serpent compiler not supported");
//    }
//
//    @Override
//    public String eth_resend() {
//        throw new UnsupportedOperationException("JSON RPC method eth_resend not implemented yet");
//    }
//
//    @Override
//    public String eth_pendingTransactions() {
//        throw new UnsupportedOperationException("JSON RPC method eth_pendingTransactions not implemented yet");
//    }

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
                    logFilter.withTopic((byte[][]) null);
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

        return toJsonHex(id);
    }

    @Override
    public String eth_newBlockFilter() {
        int id = filterCounter.getAndIncrement();
        installedFilters.put(id, new NewBlockFilter());
        return toJsonHex(id);
    }

    @Override
    public String eth_newPendingTransactionFilter() {
        int id = filterCounter.getAndIncrement();
        installedFilters.put(id, new PendingTransactionFilter());
        return toJsonHex(id);
    }

    @Override
    public boolean eth_uninstallFilter(String id) {
        if (id == null) return false;
        return installedFilters.remove(StringHexToBigInteger(id).intValue()) != null;
    }

    @Override
    public Object[] eth_getFilterChanges(String id) {
        Filter filter = installedFilters.get(StringHexToBigInteger(id).intValue());
        if (filter == null) return null;
        return filter.poll();
    }

    @Override
    public Object[] eth_getFilterLogs(String id) {
        log.debug("eth_getFilterLogs ...");
        return eth_getFilterChanges(id);
    }

    @Override
    public Object[] eth_getLogs(FilterRequest filterRequest) throws Exception {
        log.debug("eth_getLogs ...");
        String id = eth_newFilter(filterRequest);
        Object[] ret = eth_getFilterChanges(id);
        eth_uninstallFilter(id);
        return ret;
    }

//    @Override
//    public String eth_getWork() {
//        throw new UnsupportedOperationException("JSON RPC method eth_getWork not implemented yet");
//    }
//
//    @Override
//    public String eth_submitWork() {
//        throw new UnsupportedOperationException("JSON RPC method eth_submitWork not implemented yet");
//    }
//
//    @Override
//    public String eth_submitHashrate() {
//        throw new UnsupportedOperationException("JSON RPC method eth_submitHashrate not implemented yet");
//    }
//
//    @Override
//    public String db_putString() {
//        throw new UnsupportedOperationException("JSON RPC method db_putString not implemented yet");
//    }
//
//    @Override
//    public String db_getString() {
//        throw new UnsupportedOperationException("JSON RPC method db_getString not implemented yet");
//    }
//
//    @Override
//    public String db_putHex() {
//        throw new UnsupportedOperationException("JSON RPC method db_putHex not implemented yet");
//    }
//
//    @Override
//    public String db_getHex() {
//        throw new UnsupportedOperationException("JSON RPC method db_getHex not implemented yet");
//    }
//
//    @Override
//    public String shh_post() {
//        throw new UnsupportedOperationException("JSON RPC method shh_post not implemented yet");
//    }
//
//    @Override
//    public String shh_version() {
//        throw new UnsupportedOperationException("JSON RPC method shh_version not implemented yet");
//    }
//
//    @Override
//    public String shh_newIdentity() {
//        throw new UnsupportedOperationException("JSON RPC method shh_newIdentity not implemented yet");
//    }
//
//    @Override
//    public String shh_hasIdentity() {
//        throw new UnsupportedOperationException("JSON RPC method shh_hasIdentity not implemented yet");
//    }
//
//    @Override
//    public String shh_newGroup() {
//        throw new UnsupportedOperationException("JSON RPC method shh_newGroup not implemented yet");
//    }
//
//    @Override
//    public String shh_addToGroup() {
//        throw new UnsupportedOperationException("JSON RPC method shh_addToGroup not implemented yet");
//    }
//
//    @Override
//    public String shh_newFilter() {
//        throw new UnsupportedOperationException("JSON RPC method shh_newFilter not implemented yet");
//    }
//
//    @Override
//    public String shh_uninstallFilter() {
//        throw new UnsupportedOperationException("JSON RPC method shh_uninstallFilter not implemented yet");
//    }
//
//    @Override
//    public String shh_getFilterChanges() {
//        throw new UnsupportedOperationException("JSON RPC method shh_getFilterChanges not implemented yet");
//    }
//
//    @Override
//    public String shh_getMessages() {
//        throw new UnsupportedOperationException("JSON RPC method shh_getMessages not implemented yet");
//    }
//
//    @Override
//    public boolean admin_addPeer(String enodeUrl) {
//        eth.connect(new Node(enodeUrl));
//        return true;
//    }
//
//    @Override
//    public String admin_exportChain() {
//        throw new UnsupportedOperationException("JSON RPC method admin_exportChain not implemented yet");
//    }
//
//    @Override
//    public String admin_importChain() {
//        throw new UnsupportedOperationException("JSON RPC method admin_importChain not implemented yet");
//    }
//
//    @Override
//    public String admin_sleepBlocks() {
//        throw new UnsupportedOperationException("JSON RPC method admin_sleepBlocks not implemented yet");
//    }
//
//    @Override
//    public String admin_verbosity() {
//        throw new UnsupportedOperationException("JSON RPC method admin_verbosity not implemented yet");
//    }
//
//    @Override
//    public String admin_setSolc() {
//        throw new UnsupportedOperationException("JSON RPC method admin_setSolc not implemented yet");
//    }
//
//    @Override
//    public String admin_startRPC() {
//        throw new UnsupportedOperationException("JSON RPC method admin_startRPC not implemented yet");
//    }
//
//    @Override
//    public String admin_stopRPC() {
//        throw new UnsupportedOperationException("JSON RPC method admin_stopRPC not implemented yet");
//    }
//
//    @Override
//    public String admin_setGlobalRegistrar() {
//        throw new UnsupportedOperationException("JSON RPC method admin_setGlobalRegistrar not implemented yet");
//    }
//
//    @Override
//    public String admin_setHashReg() {
//        throw new UnsupportedOperationException("JSON RPC method admin_setHashReg not implemented yet");
//    }
//
//    @Override
//    public String admin_setUrlHint() {
//        throw new UnsupportedOperationException("JSON RPC method admin_setUrlHint not implemented yet");
//    }
//
//    @Override
//    public String admin_saveInfo() {
//        throw new UnsupportedOperationException("JSON RPC method admin_saveInfo not implemented yet");
//    }
//
//    @Override
//    public String admin_register() {
//        throw new UnsupportedOperationException("JSON RPC method admin_register not implemented yet");
//    }
//
//    @Override
//    public String admin_registerUrl() {
//        throw new UnsupportedOperationException("JSON RPC method admin_registerUrl not implemented yet");
//    }
//
//    @Override
//    public String admin_startNatSpec() {
//        throw new UnsupportedOperationException("JSON RPC method admin_startNatSpec not implemented yet");
//    }
//
//    @Override
//    public String admin_stopNatSpec() {
//        throw new UnsupportedOperationException("JSON RPC method admin_stopNatSpec not implemented yet");
//    }
//
//    @Override
//    public String admin_getContractInfo() {
//        throw new UnsupportedOperationException("JSON RPC method admin_getContractInfo not implemented yet");
//    }
//
//    @Override
//    public String admin_httpGet() {
//        throw new UnsupportedOperationException("JSON RPC method admin_httpGet not implemented yet");
//    }
//
//    @Override
//    public String admin_nodeInfo() {
//        throw new UnsupportedOperationException("JSON RPC method admin_nodeInfo not implemented yet");
//    }
//
//    @Override
//    public String admin_peers() {
//        throw new UnsupportedOperationException("JSON RPC method admin_peers not implemented yet");
//    }
//
//    @Override
//    public String admin_datadir() {
//        throw new UnsupportedOperationException("JSON RPC method admin_datadir not implemented yet");
//    }
//
//    @Override
//    public String net_addPeer() {
//        throw new UnsupportedOperationException("JSON RPC method net_addPeer not implemented yet");
//    }

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

//    @Override
//    public boolean miner_startAutoDAG() {
//        return false;
//    }
//
//    @Override
//    public boolean miner_stopAutoDAG() {
//        return false;
//    }
//
//    @Override
//    public boolean miner_makeDAG() {
//        return false;
//    }
//
//    @Override
//    public String miner_hashrate() {
//        return "0x01";
//    }

//    @Override
//    public String debug_printBlock() {
//        throw new UnsupportedOperationException("JSON RPC method debug_printBlock not implemented yet");
//    }
//
//    @Override
//    public String debug_getBlockRlp() {
//        throw new UnsupportedOperationException("JSON RPC method debug_getBlockRlp not implemented yet");
//    }
//
//    @Override
//    public String debug_setHead() {
//        throw new UnsupportedOperationException("JSON RPC method debug_setHead not implemented yet");
//    }
//
//    @Override
//    public String debug_processBlock() {
//        throw new UnsupportedOperationException("JSON RPC method debug_processBlock not implemented yet");
//    }

//    @Override
//    public String debug_seedHash() {
//        throw new UnsupportedOperationException("JSON RPC method debug_seedHash not implemented yet");
//    }
//
//    @Override
//    public String debug_dumpBlock() {
//        throw new UnsupportedOperationException("JSON RPC method debug_dumpBlock not implemented yet");
//    }
//
//    @Override
//    public String debug_metrics() {
//        throw new UnsupportedOperationException("JSON RPC method debug_metrics not implemented yet");
//    }

    @Override
    public String personal_newAccount(@NonNull String password) {
        log.debug("personal_newAccount(...)");
        // generate new private key
        ECKey key = new ECKey();
        Account account = importAccount(key, password);
        return toJsonHex(account.getAddress());
    }

    public String personal_importRawKey(String keydata, String passphrase) {
        log.debug("personal_importRawKey(...)");
        Objects.requireNonNull(keydata, "keydata is required");
        Objects.requireNonNull(passphrase, "passphrase is required");

        Account account = importAccount(ECKey.fromPrivate(Hex.decode(keydata)), passphrase);
        return toJsonHex(account.getAddress());
    }

    @Override
    public boolean personal_unlockAccount(String address, String password, String duration) {
        log.info("personal_unlockAccount(" + address + ", ...)");

        Objects.requireNonNull(address, "address is required");
        Objects.requireNonNull(password, "password is required");

        final ECKey key = keystore.loadStoredKey(JSonHexToHex(address).toLowerCase(), password);
        if (key != null) {
            log.info("Found key address is " + Hex.toHexString(key.getAddress()));
            final Account account = new Account();
            account.init(key);
            log.info("Found account address is " + Hex.toHexString(account.getAddress()));
            unlockedAccounts.put(Hex.toHexString(account.getAddress()).toLowerCase(), account);
            return true;
        } else {
            // we can return false or send description message with exception
            // prefer exception for now
            throw new RuntimeException("No key was found in keystore for account: " + address);
        }
    }

    @Override
    public boolean personal_lockAccount(String address) {
        Objects.requireNonNull(address, "address is required");

        unlockedAccounts.remove(address.toLowerCase());
        return true;
    }

    @Override
    public String[] personal_listAccounts() {
        return keystore.listStoredKeys();
    }

    /**
     * List method names for client side terminal competition.
     * @return array in format: `["methodName arg1 arg2", "methodName2"]`
     */
    @Override
    public String[] ethj_listAvailableMethods() {
        final Set<String> ignore = Arrays.asList(Object.class.getMethods()).stream()
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        return Arrays.asList(JsonRpcImpl.class.getMethods()).stream()
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !ignore.contains(method.getName()))
                .map(method -> {
                     List<String> params = Arrays.asList(method.getParameters())
                            .stream()
                            .map(parameter ->
                                    parameter.isNamePresent() ? parameter.getName() : parameter.getType().getSimpleName())
                            .collect(Collectors.toList());
                    params.add(0, method.getName());
                    return params.stream().collect(Collectors.joining(" "));
                })
                .sorted(String::compareTo)
                .toArray(size -> new String[size]);
    }
}
