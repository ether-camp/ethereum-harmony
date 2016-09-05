package com.ethercamp.harmony.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import com.ethercamp.harmony.dto.*;
import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.sync.SyncManager;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Created by Stan Reshetnyk on 11.07.16.
 */
@Service
@Slf4j(topic = "harmony")
public class BlockchainInfoService implements ApplicationListener {

    public static final int KEEP_LOG_ENTRIES = 1000;
    private static final int BLOCK_COUNT_FOR_HASH_RATE = 100;
    private static final int KEEP_BLOCKS_FOR_CLIENT = 50;

    @Autowired
    private Environment env;

    @Autowired
    private ClientMessageService clientMessageService;

    @Autowired
    private Ethereum ethereum;

    @Autowired
    private Blockchain blockchain;

    @Autowired
    private SyncManager syncManager;

    @Autowired
    SystemProperties config;

    @Autowired
    ChannelManager channelManager;

    /**
     * Concurrent queue of last blocks.
     * Ethereum adds items when available.
     * Service reads items with interval.
     */
    private final Queue<Block> lastBlocksForHashRate = new ConcurrentLinkedQueue();

    private final Queue<BlockInfo> lastBlocksForClient = new ConcurrentLinkedQueue();

    private final AtomicReference<MachineInfoDTO> machineInfo = new AtomicReference<>(new MachineInfoDTO(0, 0l, 0l, 0l));

    private final AtomicReference<BlockchainInfoDTO> blockchainInfo = new AtomicReference<>();

    private final AtomicReference<NetworkInfoDTO> networkInfo = new AtomicReference<>();

    private final AtomicReference<InitialInfoDTO> initialInfo = new AtomicReference<>();

    private final Queue<String> lastLogs = new ConcurrentLinkedQueue();

    public InitialInfoDTO getInitialInfo() {
        return initialInfo.get();
    }

    protected volatile SyncStatus syncStatus = SyncStatus.LONG_SYNC;


    @PostConstruct
    private void postConstruct() {
        /**
         * - gather blocks to calculate hash rate;
         * - gather blocks to keep for client block tree;
         * - notify client on new block;
         * - track sync status.
         */
        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onBlock(Block block, List<TransactionReceipt> receipts) {
                addBlock(block);
            }
        });

        if (!config.isSyncEnabled()) {
            syncStatus = BlockchainInfoService.SyncStatus.DISABLED;
        } else {
            ethereum.addListener(new EthereumListenerAdapter() {
                @Override
                public void onSyncDone() {
                    log.info("Sync done");
                    syncStatus = BlockchainInfoService.SyncStatus.SHORT_SYNC;
                }
            });
        }


        createLogAppenderForMessaging();


        final long lastBlock = blockchain.getBestBlock().getNumber();
        final long startImportBlock = Math.max(0, lastBlock - Math.max(BLOCK_COUNT_FOR_HASH_RATE, KEEP_BLOCKS_FOR_CLIENT));

        LongStream.rangeClosed(startImportBlock, lastBlock)
                .forEach(i -> {
                    log.debug("Imported block " + i);
                    addBlock(blockchain.getBlockByNumber(i));
                });
    }

    private void addBlock(Block block) {
        lastBlocksForHashRate.add(block);

        if (lastBlocksForHashRate.size() > BLOCK_COUNT_FOR_HASH_RATE) {
            lastBlocksForHashRate.poll();
        }
        if (lastBlocksForClient.size() > KEEP_BLOCKS_FOR_CLIENT) {
            lastBlocksForClient.poll();
        }

        BlockInfo blockInfo = new BlockInfo(
                block.getNumber(),
                Hex.toHexString(block.getHash()),
                Hex.toHexString(block.getParentHash()),
                block.getDifficultyBI().longValue()
        );
        lastBlocksForClient.add(blockInfo);
        clientMessageService.sendToTopic("/topic/newBlockInfo", blockInfo);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof EmbeddedServletContainerInitializedEvent) {
            final int port = ((EmbeddedServletContainerInitializedEvent) event).getEmbeddedServletContainer().getPort();

            final boolean isPrivateNetwork = env.getProperty("networkProfile", "").equalsIgnoreCase("private");
            final boolean isClassicNetwork = env.getProperty("networkProfile", "").equalsIgnoreCase("classic");

            final Optional<String> blockHash = Optional.ofNullable(blockchain.getBlockByNumber(0l))
                    .map(block -> Hex.toHexString(block.getHash()));
            final String networkName;
            if (isPrivateNetwork) {
                networkName = "Private Miner Network";
            } else if (isClassicNetwork) {
                networkName = "Ethereum Classic";
            } else {
                networkName = blockHash
                        .map(hash -> BlockchainConsts.GENESIS_BLOCK_HASH_MAP.getOrDefault(hash, "Unknown network"))
                        .orElse("Undefined blockchain");
            }

            initialInfo.set(new InitialInfoDTO(
                    env.getProperty("ethereumJ.version"),
                    env.getProperty("app.version"),
                    networkName,
                    blockHash.orElse(null),
                    System.currentTimeMillis(),
                    Hex.toHexString(config.nodeId()),
                    port,
                    isPrivateNetwork,
                    env.getProperty("portCheckerUrl")
            ));
        }
    }

    public MachineInfoDTO getMachineInfo() {
        return machineInfo.get();
    }

    public Queue<String> getSystemLogs() {
        return lastLogs;
    }

    public Queue<BlockInfo> getBlocks() {
        return lastBlocksForClient;
    }

    @Scheduled(fixedRate = 5000)
    private void doUpdateMachineInfoStatus() {

        final OperatingSystemMXBean bean = (OperatingSystemMXBean) ManagementFactory
                .getOperatingSystemMXBean();

        machineInfo.set(new MachineInfoDTO(
                ((Double) (bean.getSystemCpuLoad() * 100)).intValue(),
                bean.getFreePhysicalMemorySize(),
                bean.getTotalPhysicalMemorySize(),
                getFreeDiskSpace()
        ));

        clientMessageService.sendToTopic("/topic/machineInfo", machineInfo.get());
    }

    @Scheduled(fixedRate = 2000)
    private void doUpdateBlockchainStatus() {

        final Block bestBlock = ethereum.getBlockchain().getBestBlock();

        blockchainInfo.set(
                new BlockchainInfoDTO(
                        syncManager.getLastKnownBlockNumber(),
                        bestBlock.getNumber(),
                        bestBlock.getTimestamp(),
                        bestBlock.getTransactionsList().size(),
                        bestBlock.getDifficultyBI().longValue(),
                        0l, // not implemented
                        calculateHashRate(),
                        ethereum.getGasPrice()
                )
        );

        clientMessageService.sendToTopic("/topic/blockchainInfo", blockchainInfo.get());
    }

    @Scheduled(fixedRate = 2000)
    private void doUpdateNetworkInfo() {
        final NetworkInfoDTO info = new NetworkInfoDTO(
                channelManager.getActivePeers().size(),
                syncStatus.toString(),
                config.listenPort(),
                true
        );

        final HashMap<String, Integer> miners = new HashMap<>();
        lastBlocksForHashRate
                .stream()
                .forEach(b -> {
                    String minerAddress = Hex.toHexString(b.getCoinbase());
                    int count = miners.containsKey(minerAddress) ? miners.get(minerAddress) : 0;
                    miners.put(minerAddress, count + 1);
                });

        final List<MinerDTO> minersList = miners.entrySet().stream()
                .map(entry -> new MinerDTO(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount()))
                .limit(3)
                .collect(Collectors.toList());
        info.getMiners().addAll(minersList);

        networkInfo.set(info);

        clientMessageService.sendToTopic("/topic/networkInfo", info);
    }

    private long calculateHashRate() {
        final List<Block> blocks = Arrays.asList(lastBlocksForHashRate.toArray(new Block[0]));

        if (blocks.isEmpty()) {
            return 0;
        }

        final Block bestBlock = blocks.get(blocks.size() - 1);
        final long difficulty = bestBlock.getDifficultyBI().longValue();

        final long sumTimestamps = blocks.stream().mapToLong(b -> b.getTimestamp()).sum();
        if (sumTimestamps > 0) {
            return difficulty / (sumTimestamps / blocks.size() / 1000);
        } else {
            return 0l;
        }
    }

    /**
     * Get free space of disk where project located.
     * Verified on multi disk Windows.
     * Not tested against sym links
     */
    private long getFreeDiskSpace() {
        final File currentDir = new File(".");
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
//            log.debug(root.toAbsolutePath() + " vs current " + currentDir.getAbsolutePath());
            try {
                final FileStore store = Files.getFileStore(root);

                final boolean isCurrentDirBelongsToRoot = Paths.get(currentDir.getAbsolutePath()).startsWith(root.toAbsolutePath());
                if (isCurrentDirBelongsToRoot) {
                    final long usableSpace = store.getUsableSpace();
//                    log.debug("Disk available:" + readableFileSize(usableSpace)
//                            + ", total:" + readableFileSize(store.getTotalSpace()));
                    return usableSpace;
                }
            } catch (IOException e) {
                log.error("Problem querying space: " + e.toString());
            }
        }
        return 0;
    }

    // for better logs
    private String readableFileSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * Create log appender, which will subscribe to loggers, we are interested in.
     * Appender will send logs to messaging topic then (for delivering to client side).
     */
    private void createLogAppenderForMessaging() {
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        final PatternLayout patternLayout = new PatternLayout();
        patternLayout.setPattern("%d %-5level [%thread] %logger{35} - %msg%n");
        patternLayout.setContext(context);
        patternLayout.start();

        final UnsynchronizedAppenderBase messagingAppender = new UnsynchronizedAppenderBase() {
            @Override
            protected void append(Object eventObject) {
                LoggingEvent event = (LoggingEvent) eventObject;
                String message = patternLayout.doLayout(event);
                lastLogs.add(message);
                if (lastLogs.size() > KEEP_LOG_ENTRIES) {
                    lastLogs.poll();
                }
                clientMessageService.sendToTopic("/topic/systemLog", message);
            }
        };

        Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);


        // No effect of this
        final LevelFilter filter = new LevelFilter();
        filter.setLevel(Level.INFO);
        messagingAppender.addFilter(filter);
        messagingAppender.setName("ClientMessagingAppender");
        messagingAppender.setContext(context);

        root.addAppender(messagingAppender);

        messagingAppender.start();
    }

    static class BlockchainConsts {

        static final Map<String, String> GENESIS_BLOCK_HASH_MAP = new HashMap<>();

        static {
            GENESIS_BLOCK_HASH_MAP.put("d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3", "Live ETH");
            GENESIS_BLOCK_HASH_MAP.put("0cd786a2425d16f152c658316c423e6ce1181e15c3295826d7c9904cba9ce303", "Morden ETH");

            // not static as user can put changes into genesis, which cause hash to change
            // GENESIS_BLOCK_HASH_MAP.put("???", "Private Miner Network");
        }
    }

    /**
     * Created by Stan Reshetnyk on 09.08.16.
     */
    public static enum SyncStatus {
        DISABLED,
        LONG_SYNC,
        SHORT_SYNC
    }
}
