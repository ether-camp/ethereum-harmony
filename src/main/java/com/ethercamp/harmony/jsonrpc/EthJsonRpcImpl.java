/*
 * Copyright 2015, 2016 Ether.Camp Inc. (US)
 * This file is part of Ethereum Harmony.
 *
 * Ethereum Harmony is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Ethereum Harmony is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Ethereum Harmony.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ethercamp.harmony.jsonrpc;

import com.ethercamp.harmony.config.RpcEnabledCondition;
import com.ethercamp.harmony.keystore.Keystore;
import com.ethercamp.harmony.model.Account;
import com.ethercamp.harmony.service.BlockchainInfoService;
import com.ethercamp.harmony.service.PrivateMinerService;
import com.ethercamp.harmony.service.WalletService;
import com.ethercamp.harmony.util.ErrorCodes;
import com.ethercamp.harmony.util.exception.HarmonyException;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;
import org.ethereum.config.CommonConfig;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockchainImpl;
import org.ethereum.core.Bloom;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.PendingStateImpl;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.core.TransactionInfo;
import org.ethereum.db.TransactionStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.LogFilter;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.manager.WorldManager;
import org.ethereum.mine.BlockMiner;
import org.ethereum.mine.EthashAlgo;
import org.ethereum.mine.MinerIfc;
import org.ethereum.mine.MinerListener;
import org.ethereum.net.client.Capability;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.rlpx.discover.NodeManager;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.sync.SyncManager;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.spongycastle.util.encoders.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ethercamp.harmony.jsonrpc.TypeConverter.*;
import static java.math.BigInteger.valueOf;
import static org.ethereum.crypto.HashUtil.sha3;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ethereum.util.ByteUtil.bigIntegerToBytes;
import static org.ethereum.util.ByteUtil.hexStringToBytes;
import static org.ethereum.util.ByteUtil.longToBytes;

/**
 * @author Anton Nashatyrev
 */
@Slf4j(topic = "jsonrpc")
@Service
@Conditional(RpcEnabledCondition.class)
// renamed to not conflict with class from core
// wait for core class to be removed
public class EthJsonRpcImpl implements JsonRpc {

    private static final String BLOCK_LATEST = "latest";

    public class BinaryCallArguments {
        public long nonce;
        public long gasPrice;
        public long gasLimit;
        public String toAddress;
        public String fromAddress;
        public long value;
        public byte[] data;
        public void setArguments(CallArguments args) throws Exception {
            nonce = 0;
            if (args.nonce != null && args.nonce.length() != 0)
                nonce = jsonHexToLong(args.nonce);

            gasPrice = 0;
            if (args.gasPrice != null && args.gasPrice.length()!=0)
                gasPrice = jsonHexToLong(args.gasPrice);

            gasLimit = 4_000_000;
            if (args.gas != null && args.gas.length()!=0)
                gasLimit = jsonHexToLong(args.gas);

            toAddress = null;
            if (args.to != null && !args.to.isEmpty())
                toAddress = jsonHexToHex(args.to);

            fromAddress = null;
            if (args.from != null && !args.from.isEmpty())
                fromAddress = jsonHexToHex(args.from);

            value=0;
            if (args.value != null && args.value.length()!=0)
                value = jsonHexToLong(args.value);

            data = null;

            if (args.data != null && args.data.length()!=0)
                data = TypeConverter.hexToByteArray(args.data);
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
    NodeManager nodeManager;

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

    @Autowired
    BlockStore blockStore;

    @Autowired
    ProgramInvokeFactory programInvokeFactory;

    @Autowired
    CommonConfig commonConfig = CommonConfig.getDefault();

    @Autowired
    WalletService walletService;

    @Autowired
    PrivateMinerService privateMinerService;

    @Autowired
    BlockchainInfoService blockchainInfoService;

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
    Map<ByteArrayWrapper, TransactionReceipt> pendingReceipts = Collections.synchronizedMap(new LRUMap<>(1024));

    Map<ByteArrayWrapper, Block> miningBlocks = new ConcurrentHashMap<>();

    volatile Block miningBlock;

    volatile SettableFuture<MinerIfc.MiningResult> miningTask;

    final MinerIfc externalMiner = new MinerIfc() {
        @Override
        public ListenableFuture<MiningResult> mine(Block block) {
            miningBlock = block;
            miningTask = SettableFuture.create();
            return miningTask;
        }

        @Override
        public boolean validate(BlockHeader blockHeader) {
            return false;
        }

        @Override
        public void setListeners(Collection<MinerListener> listeners) {}
    };

    boolean minerInitialized = false;

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
                    for (Filter filter : installedFilters.values()) {
                        filter.updatePendingTx(txReceipt);
                    }
                } else {
                    pendingReceipts.remove(txHashW);
                }
            }
        });

    }

    private long jsonHexToLong(String x) throws Exception {
        if (!x.startsWith("0x"))
            throw new Exception("Incorrect hex syntax");
        x = x.substring(2);
        return Long.parseLong(x, 16);
    }

    private int jsonHexToInt(String x) throws Exception {
        if (!x.startsWith("0x"))
            throw new Exception("Incorrect hex syntax");
        x = x.substring(2);
        return Integer.parseInt(x, 16);
    }

    private String jsonHexToHex(String x) {
        if (!x.startsWith("0x"))
            throw new RuntimeException("Incorrect hex syntax");
        x = x.substring(2);
        return x;
    }

    private Block getBlockByJSonHash(String blockHash) throws Exception {
        byte[] bhash = TypeConverter.hexToByteArray(blockHash);
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
            long blockNumber = hexToBigInteger(id).longValue();
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
     * @throws RuntimeException if account is not unlocked or not found in keystore
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
            throw new HarmonyException("Unlocked account is required. Account: " + address, ErrorCodes.ERROR__101_UNLOCK_ACCOUNT);
        } else {
            throw new HarmonyException("Key not found in keystore", ErrorCodes.ERROR__102_KEY_NOT_FOUND);
        }
    }

    protected Account importAccount(ECKey key, String password) {
        return walletService.importPersonal(key, password);
    }

    public String web3_clientVersion() {
        Pattern shortVersion = Pattern.compile("(\\d\\.\\d).*");
        Matcher matcher = shortVersion.matcher(System.getProperty("java.version"));
        matcher.matches();

        return Arrays.asList(
                "Harmony", "v" + config.projectVersion(),
                System.getProperty("os.name"),
                "Java" + matcher.group(1),
                config.projectVersionModifier() + "-" + BuildInfo.buildHash).stream()
                .collect(Collectors.joining("/"));
    }

    public String web3_sha3(String data) throws Exception {
        byte[] result = HashUtil.sha3(TypeConverter.hexToByteArray(data));
        return TypeConverter.toJsonHex(result);
    }

    /**
     * Returns the current network id.
     */
    public String net_version() {
        return String.valueOf(config.networkId());
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
                .filter(Capability::isEth)
                .map(p -> ((Byte) p.getVersion()).intValue())
                .reduce(0, Math::max)
                .toString();
    }

    public Object eth_syncing() {
        if (!config.isSyncEnabled()) {
            return false;
        } else {
            return new SyncingResult(
                    TypeConverter.toJsonHex(initialBlockNumber),
                    TypeConverter.toJsonHex(blockchain.getBestBlock().getNumber()),
                    TypeConverter.toJsonHex(syncManager.getLastKnownBlockNumber())
            );
        }
    }

    public String eth_coinbase() {
        return toJsonHex(blockchain.getMinerCoinbase());
    }

    public boolean eth_mining() {
        return blockMiner.isMining();
    }

    public String eth_hashrate() {
        if (!blockMiner.isMining()) {
            return null;
        } else {
            return toJsonHex(privateMinerService.calcAvgHashRate());
        }
    }

    public String eth_gasPrice(){
        return TypeConverter.toJsonHex(blockchainInfoService.getRecommendedGasPrice());
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

        byte[] addressAsByteArray = TypeConverter.hexToByteArray(address);
        BigInteger balance = getRepoByJsonBlockId(blockId).getBalance(addressAsByteArray);
        return TypeConverter.toJsonHex(balance);
    }

    public String eth_getLastBalance(String address) throws Exception {
        return eth_getBalance(address, BLOCK_LATEST);
    }

    @Override
    public String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception {
        byte[] addressAsByteArray = hexToByteArray(address);
        DataWord storageValue = getRepoByJsonBlockId(blockId).
                getStorageValue(addressAsByteArray, DataWord.of(hexToByteArray(storageIdx)));
        return storageValue != null ? TypeConverter.toJsonHex(storageValue.getData()) : null;
    }

    @Override
    public String eth_getTransactionCount(String address, String blockId) throws Exception {
        byte[] addressAsByteArray = TypeConverter.hexToByteArray(address);
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
        byte[] addressAsByteArray = TypeConverter.hexToByteArray(address);
        byte[] code = getRepoByJsonBlockId(blockId).getCode(addressAsByteArray);
        return TypeConverter.toJsonHex(code);
    }

    /**
     * Sign message hash with key to produce Elliptic Curve Digital Signature (ECDSA) signature.
     *
     * The sign method calculates an Ethereum specific signature with:
     * sign(keccak256("\x19Ethereum Signed Message:\n" + len(message) + message))).
     *
     * @param address - address to sign. Account must be unlocked
     * @param msg - message
     * @return ECDSA signature (in hex)
     * @throws Exception
     */
    public String eth_sign(String address, String msg) throws Exception {
        String ha = jsonHexToHex(address);
        Account account = getAccountFromKeystore(ha);

        String origMsg = new String(hexToByteArray(msg));
        // 0x19 = 25, length should be an ascii decimals, message - original
        String message = (char) 25 + "Ethereum Signed Message:\n" + origMsg.length() + origMsg;

        ECKey.ECDSASignature signature = account.getEcKey().sign(sha3(message.getBytes()));
        byte[] signatureBytes = toByteArray(signature);

        return TypeConverter.toJsonHex(signatureBytes);
    }

    private byte[] toByteArray(ECKey.ECDSASignature signature) {
        return ByteUtil.merge(
                bigIntegerToBytes(signature.r),
                bigIntegerToBytes(signature.s),
                new byte[] {signature.v});
    }

    public String eth_sendTransaction(CallArguments args) throws Exception {
        Account account = getAccountFromKeystore(jsonHexToHex(args.from));

        return sendTransaction(args, account);
    }

    private String sendTransaction(CallArguments args, Account account) {
        if (args.data != null && args.data.startsWith("0x"))
            args.data = args.data.substring(2);

        // convert zero to empty byte array
        // TEMP, until decide for better behavior
        final BigInteger valueBigInt = args.value != null ? hexToBigInteger(args.value) : BigInteger.ZERO;
        final byte[] value = !valueBigInt.equals(BigInteger.ZERO) ? bigIntegerToBytes(valueBigInt) : EMPTY_BYTE_ARRAY;

        final Transaction tx = new Transaction(
                args.nonce != null ? hexToByteArray(args.nonce) : bigIntegerToBytes(pendingState.getRepository().getNonce(account.getAddress())),
                args.gasPrice != null ? hexToByteArray(args.gasPrice) : ByteUtil.longToBytesNoLeadZeroes(blockchainInfoService.getRecommendedGasPrice()),
                args.gas != null ? hexToByteArray(args.gas) : longToBytes(90_000),
                args.to != null ? hexToByteArray(args.to) : EMPTY_BYTE_ARRAY,
                value,
                args.data != null ? hexToByteArray(args.data) : EMPTY_BYTE_ARRAY);

        tx.sign(account.getEcKey());

        validateAndSubmit(tx);

        return TypeConverter.toJsonHex(tx.getHash());
    }

    public String eth_sendRawTransaction(String rawData) throws Exception {
        Transaction tx = new Transaction(hexToByteArray(rawData));

        tx.rlpParse();
        validateAndSubmit(tx);

        return TypeConverter.toJsonHex(tx.getHash());
    }

    protected void validateAndSubmit(Transaction tx) {
        if (tx.getValue().length > 0 && tx.getValue()[0] == 0) {
            // zero value should be sent as empty byte array
            // otherwise tx will be accepted by core, but never included in block
//            throw new RuntimeException("Field 'value' should not have leading zero");
            log.warn("Transaction might use incorrect zero value");
        }

        eth.submitTransaction(tx);
    }

    protected TransactionReceipt createCallTxAndExecute(CallArguments args, Block block) throws Exception {
        Repository repository = ((Repository) worldManager.getRepository())
                .getSnapshotTo(block.getStateRoot())
                .startTracking();

        return createCallTxAndExecute(args, block, repository, worldManager.getBlockStore());
    }

    protected TransactionReceipt createCallTxAndExecute(CallArguments args, Block block, Repository repository, BlockStore blockStore) throws Exception {
        BinaryCallArguments bca = new BinaryCallArguments();
        bca.setArguments(args);
        Transaction rawTransaction = CallTransaction.createRawTransaction(0,
                bca.gasPrice,
                bca.gasLimit,
                bca.toAddress,
                bca.value,
                bca.data);
        LocalTransaction tx = new LocalTransaction(rawTransaction.getEncoded());

        // handle from address without signing
        if (args.from != null) {
            tx.setSender(hexStringToBytes(args.from));
        } else {
            // put mock signature if not present
            tx.sign(ECKey.DUMMY);
        }

        try {
            TransactionExecutor executor = new TransactionExecutor(
                    tx, block.getCoinbase(), repository, blockStore,
                    programInvokeFactory, block, new EthereumListenerAdapter(), 0)
                    .withCommonConfig(commonConfig)
                    .setLocalCall(true);

            executor.init();
            executor.execute();
            executor.go();
            executor.finalization();

            return executor.getReceipt();
        } finally {
            repository.rollback();
        }
    }

    public String eth_call(CallArguments args, String bnOrId) throws Exception {
        TransactionReceipt res;
        if ("pending".equals(bnOrId)) {
            Block pendingBlock = blockchain.createNewBlock(blockchain.getBestBlock(), pendingState.getPendingTransactions(), Collections.<BlockHeader>emptyList());
            res = createCallTxAndExecute(args, pendingBlock, pendingState.getRepository(), worldManager.getBlockStore());
        } else {
            res = createCallTxAndExecute(args, getByJsonBlockId(bnOrId));
        }
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
        br.number = isPending ? null : toJsonHex(block.getNumber());
        br.hash = isPending ? null : toJsonHex(block.getHash());
        br.parentHash = toJsonHex(block.getParentHash());
        br.nonce = isPending ? null : toJsonHex(block.getNonce());
        br.sha3Uncles= toJsonHex(block.getUnclesHash());
        br.logsBloom = isPending ? null : toJsonHex(block.getLogBloom());
        br.transactionsRoot = toJsonHex(block.getTxTrieRoot());
        br.stateRoot = toJsonHex(block.getStateRoot());
        br.receiptsRoot = toJsonHex(block.getReceiptsRoot());
        br.miner = isPending ? null : toJsonHex(block.getCoinbase());
        br.difficulty = toJsonHex(block.getDifficultyBI());
        br.totalDifficulty = toJsonHex(blockStore.getTotalDifficultyForHash(block.getHash()));
        if (block.getExtraData() != null)
            br.extraData = toJsonHex(block.getExtraData());
        br.size = toJsonHex(block.getEncoded().length);
        br.gasLimit = toJsonHex(block.getGasLimit());
        br.gasUsed = toJsonHex(block.getGasUsed());
        br.timestamp = toJsonHex(block.getTimestamp());

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
        final Block b = getBlockByJSonHash(blockHash);
        return getBlockResult(b, fullTransactionObjects);
    }

    public BlockResult eth_getBlockByNumber(String bnOrId, Boolean fullTransactionObjects) throws Exception {
        final Block b;
        if ("pending".equalsIgnoreCase(bnOrId)) {
            b = blockchain.createNewBlock(blockchain.getBestBlock(), pendingState.getPendingTransactions(), Collections.<BlockHeader>emptyList());
        } else {
            b = getByJsonBlockId(bnOrId);
        }
        return (b == null ? null : getBlockResult(b, fullTransactionObjects));
    }

    public TransactionResultDTO eth_getTransactionByHash(String transactionHash) throws Exception {
        final byte[] txHash = hexToByteArray(transactionHash);

        final TransactionInfo txInfo = blockchain.getTransactionInfo(txHash);
        if (txInfo == null) {
            return null;
        }

        final Block block = blockchain.getBlockByHash(txInfo.getBlockHash());
        // need to return txes only from main chain
        final Block mainBlock = blockchain.getBlockByNumber(block.getNumber());
        if (!Arrays.equals(block.getHash(), mainBlock.getHash())) {
            return null;
        }
        txInfo.setTransaction(block.getTransactionsList().get(txInfo.getIndex()));

        return new TransactionResultDTO(block, txInfo.getIndex(), txInfo.getReceipt().getTransaction());
    }

    public TransactionResultDTO eth_getTransactionByBlockHashAndIndex(String blockHash, String index) throws Exception {
        Block b = getBlockByJSonHash(blockHash);
        if (b == null) return null;
        int idx = jsonHexToInt(index);
        if (idx >= b.getTransactionsList().size()) return null;
        Transaction tx = b.getTransactionsList().get(idx);
        return new TransactionResultDTO(b, idx, tx);
    }

    public TransactionResultDTO eth_getTransactionByBlockNumberAndIndex(String bnOrId, String index) throws Exception {
        Block b = getByJsonBlockId(bnOrId);
        List<Transaction> txs = getTransactionsByJsonBlockId(bnOrId);
        if (txs == null) return null;
        int idx = jsonHexToInt(index);
        if (idx >= txs.size()) return null;
        Transaction tx = txs.get(idx);
        return new TransactionResultDTO(b, idx, tx);
    }

    public TransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception {
        final byte[] hash = TypeConverter.hexToByteArray(transactionHash);

        final TransactionInfo txInfo = blockchain.getTransactionInfo(hash);

        if (txInfo == null)
            return null;

        final Block block = blockchain.getBlockByHash(txInfo.getBlockHash());
        final Block mainBlock = blockchain.getBlockByNumber(block.getNumber());

        // need to return txes only from main chain
        if (!Arrays.equals(block.getHash(), mainBlock.getHash())) {
            return null;
        }

        return new TransactionReceiptDTO(block, txInfo);
    }

    @Override
    public TransactionReceiptDTOExt ethj_getTransactionReceipt(String transactionHash) throws Exception {
        byte[] hash = TypeConverter.hexToByteArray(transactionHash);

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
        Block block = blockchain.getBlockByHash(hexToByteArray(blockHash));
        if (block == null) return null;
        int idx = jsonHexToInt(uncleIdx);
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

    @Override
    public CompilationResult eth_compileSolidity(String contract) throws Exception {
        SolidityCompiler.Result res = SolidityCompiler.compile(
                contract.getBytes(), true, SolidityCompiler.Options.ABI, SolidityCompiler.Options.BIN, SolidityCompiler.Options.INTERFACE);
        if (res.isFailed()) {
            throw new RuntimeException("Compilation error: " + res.errors);
        }
        org.ethereum.solidity.compiler.CompilationResult result = org.ethereum.solidity.compiler.CompilationResult.parse(res.output);
        CompilationResult ret = new CompilationResult();
        org.ethereum.solidity.compiler.CompilationResult.ContractMetadata contractMetadata = result.getContracts().iterator().next();
        ret.code = toJsonHex(contractMetadata.bin);
        ret.info = new CompilationInfo();
        ret.info.source = contract;
        ret.info.language = "Solidity";
        ret.info.languageVersion = "0";
        ret.info.compilerVersion = result.version;
        ret.info.abiDefinition = new CallTransaction.Contract(contractMetadata.abi).functions;
        return ret;
    }

    @Override
    public CompilationResult eth_compileLLL(String contract) {
        throw new UnsupportedOperationException("LLL compiler not supported");
    }

    @Override
    public CompilationResult eth_compileSerpent(String contract){
        throw new UnsupportedOperationException("Serpent compiler not supported");
    }
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
        private int pollStart = 0;
        static abstract class FilterEvent {
            public abstract Object getJsonEventObject();
        }
        List<FilterEvent> events = new LinkedList<>();

        public synchronized boolean hasNew() { return !events.isEmpty();}

        public synchronized Object[] poll() {
            Object[] ret = new Object[events.size() - pollStart];
            for (int i = 0; i < ret.length; i++) {
                ret[i] = events.get(i + pollStart).getJsonEventObject();
            }
            pollStart += ret.length;
            return ret;
        }

        public synchronized Object[] getAll() {
            Object[] ret = new Object[events.size()];
            for (int i = 0; i < ret.length; i++) {
                ret[i] = events.get(i).getJsonEventObject();
            }
            return ret;
        }

        protected synchronized void add(FilterEvent evt) {
            events.add(evt);
            if (events.size() > MAX_EVENT_COUNT) {
                events.remove(0);
                if (pollStart > 0) {
                    --pollStart;
                }
            }
        }

        public void newBlockReceived(Block b) {}
        public void newPendingTx(Transaction tx) {}
        public void updatePendingTx(TransactionReceipt txReceipt) {}
    }

    static class NewBlockFilter extends Filter {
        class NewBlockFilterEvent extends FilterEvent {
            private final String blockHash;
            NewBlockFilterEvent(Block b) {this.blockHash = toJsonHex(b.getHash());}

            @Override
            public String getJsonEventObject() {
                return blockHash;
            }
        }

        public void newBlockReceived(Block b) {
            add(new NewBlockFilterEvent(b));
        }
    }

    static class PendingTransactionFilter extends Filter {
        class PendingTransactionFilterEvent extends FilterEvent {
            private final String txHash;

            PendingTransactionFilterEvent(Transaction tx) {this.txHash = toJsonHex(tx.getHash());}

            @Override
            public String getJsonEventObject() {
                return txHash;
            }
        }

        public void newPendingTx(Transaction tx) {
            add(new PendingTransactionFilterEvent(tx));
        }

        @Override
        public void updatePendingTx(TransactionReceipt txReceipt) {}
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

        void onLogMatch(LogInfo logInfo, Block b, Integer txIndex, Transaction tx, int logIdx) {
            add(new LogFilterEvent(new LogFilterElement(logInfo, b, txIndex, tx, logIdx)));
        }

        void onTransactionReceipt(TransactionReceipt receipt, Block b, Integer txIndex) {
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
        public void newPendingTx(Transaction tx) {}

        @Override
        public void updatePendingTx(TransactionReceipt txReceipt) {
            if (!onPendingTx) return;
            if (logFilter.matchesContractAddress(txReceipt.getTransaction().getReceiveAddress())) {
                onTransactionReceipt(txReceipt, null, null);
            }
        }
    }

    @Override
    public String eth_newFilter(FilterRequest fr) throws Exception {
        LogFilter logFilter = new LogFilter();

        if (fr.address instanceof String) {
            logFilter.withContractAddress(hexToByteArray((String) fr.address));
        } else if (fr.address instanceof String[]) {
            List<byte[]> addr = new ArrayList<>();
            for (String s : ((String[]) fr.address)) {
                addr.add(hexToByteArray(s));
            }
            logFilter.withContractAddress(addr.toArray(new byte[0][]));
        }

        if (fr.topics != null) {
            for (Object topic : fr.topics) {
                if (topic == null) {
                    logFilter.withTopic((byte[][]) null);
                } else if (topic instanceof String) {
                    logFilter.withTopic(DataWord.of(hexToByteArray((String) topic)).getData());
                } else if (topic instanceof String[]) {
                    List<byte[]> t = new ArrayList<>();
                    for (String s : ((String[]) topic)) {
                        t.add(DataWord.of(hexToByteArray(s)).getData());
                    }
                    logFilter.withTopic(t.toArray(new byte[0][]));
                }
            }
        }

        JsonLogFilter filter = new JsonLogFilter(logFilter);
        int id = filterCounter.getAndIncrement();
        installedFilters.put(id, filter);

        Block blockFrom;
        Block blockTo;
        // EIP-234
        if (fr.blockHash != null) {
            blockFrom = getBlockByJSonHash(fr.blockHash);
            blockTo = blockFrom;
        } else {
            blockFrom = fr.fromBlock == null ? null : getByJsonBlockId(fr.fromBlock);
            blockTo = fr.toBlock == null ? null : getByJsonBlockId(fr.toBlock);
        }

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
        } else if (fr.toBlock == null || "latest".equalsIgnoreCase(fr.fromBlock) || "latest".equalsIgnoreCase(fr.toBlock)) {
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
        return installedFilters.remove(hexToBigInteger(id).intValue()) != null;
    }

    @Override
    public Object[] eth_getFilterChanges(String id) {
        Filter filter = installedFilters.get(hexToBigInteger(id).intValue());
        if (filter == null) return null;
        return filter.poll();
    }

    @Override
    public Object[] eth_getFilterLogs(String id) {
        Filter filter = installedFilters.get(hexToBigInteger(id).intValue());
        if (filter == null) return null;
        return filter.getAll();
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
    public List<Object> eth_getWork() {
        if (!minerInitialized) {
            minerInitialized = true;
            // this should initialize miningBlock
            blockMiner.setExternalMiner(externalMiner);
        }

        final Block block = miningBlock;

        if (block == null) {
            throw new RuntimeException("Mining block is not ready");
        }
        final EthashAlgo ethash = new EthashAlgo();
        final byte[] blockHash = sha3(block.getHeader().getEncodedWithoutNonce());
        final byte[] seedHash = ethash.getSeedHash(block.getNumber());
        final BigInteger target = valueOf(2).pow(256).divide(block.getDifficultyBI());

        miningBlocks.put(new ByteArrayWrapper(blockHash), block);

        return Arrays.asList(
                toJsonHex(blockHash),
                toJsonHex(seedHash),
                toJsonHex(target)
        );
    }


    @Override
    public boolean eth_submitWork(String nonceHex, String headerHex, String digestHex) throws Exception {
        try {
            final long nonce = TypeConverter.hexToLong(nonceHex);
            final byte[] digest = TypeConverter.hexToByteArray(digestHex);
            final byte[] header = TypeConverter.hexToByteArray(headerHex);

            final Block block = miningBlocks.remove(new ByteArrayWrapper(header));

            if (block != null && miningTask != null) {
                block.setNonce(longToBytes(nonce));
                block.setMixHash(digest);

                miningTask.set(new MinerIfc.MiningResult(nonce, digest, block));
                miningTask = null;
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            log.error("eth_submitWork", e);
            return false;
        }
    }

    @Override
    public boolean eth_submitHashrate(String hashrate, String id) {
        // EthereumJ doesn't support changing of miner's hashrate
        return false;
    }

    @Override
    public String shh_version() {
        return configCapabilities.getConfigCapabilities().stream()
                .filter((cap) -> Capability.SHH.equals(cap.getName()))
                .map(p -> ((Byte) p.getVersion()).intValue())
                .reduce(0, Math::max)
                .toString();
    }

    /**
     * TODO
     */
//    @Override
//    public String shh_post() {
//        throw new UnsupportedOperationException("JSON RPC method shh_post not implemented yet");
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
    @Override
    public boolean admin_addPeer(String enodeUrl) {
        final Node node = new Node(enodeUrl);
        eth.connect(node);
        nodeManager.getNodeHandler(node).getNodeStatistics().setPredefined(true);
        return true;
    }
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
    @Override
    public Map<String, ?> admin_nodeInfo() throws Exception {
        final String nodeId = Hex.toHexString(config.nodeId());
        final String listenAddr = config.bindIp() + ":" + config.listenPort();
        final BlockResult lastBlock = eth_getBlockByNumber("latest", false);

        final HashMap<String, Object> result = new HashMap<>();
        result.put("id", nodeId);
        result.put("name", web3_clientVersion());
        result.put("enode", "enode://" + nodeId + "@" + listenAddr);
        result.put("ip", config.bindIp());
        result.put("ports", ImmutableMap.of(
                "discovery", config.listenPort(),
                "listener", config.listenPort()
        ));
        result.put("listenAddr", listenAddr);
        result.put("protocols", ImmutableMap.of(
                "eth", ImmutableMap.of(
                        "network", config.getProperty("peer.networkId", "undefined"),
                        "difficulty", lastBlock.totalDifficulty,
                        "genesis", toJsonHex(config.getGenesis().getHash()),
                        "head", lastBlock.hash
                )
        ));
        return result;
    }

    @Override
    public List<Map<String, ?>> admin_peers() {
        return channelManager.getActivePeers().stream().map(c ->
                ImmutableMap.of(
                        "id", toJsonHex(c.getNodeId()),
                        "name", c.getNodeStatistics().getClientId(),
                        "caps", c.getNodeStatistics().capabilities
                                .stream()
                                .filter(cap -> c != null)
                                .map(cap -> StringUtils.capitalize(cap.getName()) + "/" + cap.getVersion())
                                .collect(Collectors.toList()),
                        "network", ImmutableMap.of(
                                // TODO put local port which initiated connection
                                "localAddress", config.bindIp() + ":" + c.getInetSocketAddress().getPort(),
                                "remoteAddress", c.getNode().getHost() + ":" + c.getNode().getPort(),
                                "hostname", c.getInetSocketAddress().getHostName(),
                        "protocols", Optional.ofNullable(c.getEthHandler())
                                .map(ethHandler -> ImmutableMap.of(
                                        "eth", ImmutableMap.of(
                                                "version", c.getEthVersion().getCode(),
                                                "difficulty", toJsonHex(ethHandler.getTotalDifficulty()),
                                                "head", Optional.ofNullable(ethHandler.getBestKnownBlock())
                                                        .map(block -> toJsonHex(ethHandler.getBestKnownBlock().getHash()))
                                                        .orElse(null)
                                        )))
                                .orElse(null)))
                ).collect(Collectors.toList());
    }
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
        log.info("miner_start requested. MaxMemory: " + Runtime.getRuntime().maxMemory());
        final long requiredMemoryBytes = 2000L << 20;   // ~2G
        if (config.isMineFullDataset() && Runtime.getRuntime().maxMemory() < requiredMemoryBytes) {
            final String errorMessage = "Not enough JVM heap (" + (Runtime.getRuntime().maxMemory() >> 20) + "Mb) to generate DAG for mining (DAG requires min 1G). It is recommended to set -Xmx3G JVM option";
            log.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
        eth.switchToShortSync().whenComplete((aVoid, throwable) -> {
            if (throwable != null) {
                log.error("Failed to switch to Short Sync and start mining", throwable);
            } else {
                log.info("Sync is switched to Short Sync or not enabled. Starting miner");
                blockMiner.startMining();
            }
        });
        return true;
    }

    @Override
    public boolean miner_stop() {
        blockMiner.stopMining();
        return true;
    }

    @Override
    public boolean miner_setEtherbase(String coinBase) throws Exception {
        blockchain.setMinerCoinbase(TypeConverter.hexToByteArray(coinBase));
        return true;
    }

    @Override
    public boolean miner_setExtra(String data) throws Exception {
        blockchain.setMinerExtraData(TypeConverter.hexToByteArray(data));
        return true;
    }

    @Override
    public boolean miner_setGasPrice(String newMinGasPrice) {
        blockMiner.setMinGasPrice(TypeConverter.hexToBigInteger(newMinGasPrice));
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

        Account account = importAccount(ECKey.fromPrivate(TypeConverter.hexToByteArray(keydata)), passphrase);
        return toJsonHex(account.getAddress());
    }

    @Override
    public boolean personal_unlockAccount(String address, String password, String duration) {
        log.info("personal_unlockAccount(" + address + ", ...)");

        Objects.requireNonNull(address, "address is required");
        Objects.requireNonNull(password, "password is required");

        final ECKey key = keystore.loadStoredKey(jsonHexToHex(address).toLowerCase(), password);
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

    @Override
    public String personal_signAndSendTransaction(CallArguments tx, String password) {
        final ECKey key = keystore.loadStoredKey(jsonHexToHex(tx.from).toLowerCase(), password);
        if (key != null) {
            final Account account = new Account();
            account.init(key);
            return sendTransaction(tx, account);
        } else {
            // we can return false or send description message with exception
            // prefer exception for now
            throw new RuntimeException("No key was found in keystore for account: " + jsonHexToHex(tx.from));
        }
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

        return Arrays.asList(EthJsonRpcImpl.class.getMethods()).stream()
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
