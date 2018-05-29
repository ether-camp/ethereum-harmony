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

package com.ethercamp.harmony.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

import com.ethercamp.harmony.keystore.FileSystemKeystore;
import com.ethercamp.harmony.util.ProcessUtils;
import org.ethereum.core.BlockHeader;
import org.ethereum.listener.RecommendedGasPriceTracker;
import org.ethereum.util.BuildInfo;
import org.ethereum.vm.VM;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import com.ethercamp.harmony.model.dto.*;
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
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

/**
 * Created by Stan Reshetnyk on 11.07.16.
 */
@Service
@Slf4j(topic = "harmony")
public class BlockchainInfoService implements ApplicationListener {

    public static final int KEEP_LOG_ENTRIES = 1000;
    private static final int BLOCK_COUNT_FOR_HASH_RATE = 100;
    private static final int KEEP_BLOCKS_FOR_CLIENT = 50;
    private static final long DB_SIZE_CACHE_EVICT_MS = 60 * 1000; // Cache DB size for ... ms

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

    @Autowired
    SystemProperties systemProperties;

    @Autowired
    FileSystemKeystore keystore;

    @Autowired
    PrivateMinerService privateMinerService;

    /**
     * Concurrent queue of last blocks.
     * Ethereum adds items when available.
     * Service reads items with interval.
     */
    private final Queue<Block> lastBlocksForHashRate = new ConcurrentLinkedQueue();

    private final Queue<BlockInfo> lastBlocksForClient = new ConcurrentLinkedQueue();

    private final AtomicReference<MachineInfoDTO> machineInfo = new AtomicReference<>(new MachineInfoDTO(0, 0l, 0l, 0l, 0l));

    private final AtomicReference<BlockchainInfoDTO> blockchainInfo = new AtomicReference<>();

    private final AtomicReference<NetworkInfoDTO> networkInfo = new AtomicReference<>();

    private final AtomicReference<InitialInfoDTO> initialInfo = new AtomicReference<>();

    private final Queue<String> lastLogs = new ConcurrentLinkedQueue();

    private volatile int serverPort;

    private long dbSizeMeasurementTime = 0;

    private long dbSize = 0;

    public InitialInfoDTO getInitialInfo() {
        return initialInfo.get();
    }

    protected volatile SyncStatus syncStatus = SyncStatus.LONG_SYNC;

    class GasPriceTracker extends RecommendedGasPriceTracker {
        public void replay(Block block) {
            super.onBlock(block);
        }
    }

    private GasPriceTracker gasPriceTracker = new GasPriceTracker();

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
        final long lastBlock = blockchain.getBestBlock().getNumber();
        for (int i = gasPriceTracker.getMinBlocks() - 1; i >= 0; --i) {
            if ((lastBlock - i) < 1) continue;
            gasPriceTracker.replay(blockchain.getBlockByNumber(lastBlock - i));
        }
        ethereum.addListener(gasPriceTracker);

        if (!config.isSyncEnabled()) {
            syncStatus = BlockchainInfoService.SyncStatus.DISABLED;
        } else {
            syncStatus = syncManager.isSyncDone() ? SyncStatus.SHORT_SYNC : SyncStatus.LONG_SYNC;
            ethereum.addListener(new EthereumListenerAdapter() {
                @Override
                public void onSyncDone(SyncState state) {
                    log.info("Sync done " + state);
                    if (syncStatus != BlockchainInfoService.SyncStatus.SHORT_SYNC) {
                        syncStatus = BlockchainInfoService.SyncStatus.SHORT_SYNC;
                    }
                }
            });
        }

        final long startImportBlock = Math.max(0, lastBlock - Math.max(BLOCK_COUNT_FOR_HASH_RATE, KEEP_BLOCKS_FOR_CLIENT));

        LongStream.rangeClosed(startImportBlock, lastBlock)
                .forEach(i -> addBlock(blockchain.getBlockByNumber(i)));
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
            serverPort = ((EmbeddedServletContainerInitializedEvent) event).getEmbeddedServletContainer().getPort();

            final boolean isPrivateNetwork = env.getProperty("networkProfile", "").equalsIgnoreCase("private");
            final boolean isClassicNetwork = env.getProperty("networkProfile", "").equalsIgnoreCase("classic");
            final boolean isCustomNetwork = env.getProperty("networkProfile", "").equalsIgnoreCase("custom");

            // find out network name
            final Optional<String> blockHash = Optional.ofNullable(blockchain.getBlockByNumber(0l))
                    .map(block -> Hex.toHexString(block.getHash()));
            final Pair<String, Optional<String>> networkInfo;
            if (isPrivateNetwork) {
                networkInfo = Pair.of("Private Miner Network", Optional.empty());
            } else if (isClassicNetwork) {
                networkInfo = Pair.of("Classic ETC", Optional.empty());
            } else if (isCustomNetwork) {
                networkInfo = Pair.of("Custom", Optional.empty());
            } else {
                networkInfo = blockHash
                        .flatMap(hash -> Optional.ofNullable(BlockchainConsts.getNetworkInfo(env, hash)))
                        .orElse(Pair.of("Unknown network", Optional.empty()));
            }


            final boolean isContractsFeatureEnabled = env.getProperty("feature.contract.enabled", "false").equalsIgnoreCase("true");
            if (!isContractsFeatureEnabled) {
                VM.setVmHook(null);
                log.info("Disabled VM hook due to contracts feature disabled");
            }

            initialInfo.set(new InitialInfoDTO(
                    config.projectVersion() + "-" + config.projectVersionModifier(),
                    "Hash: " + BuildInfo.buildHash + ",   Created: " + BuildInfo.buildTime,
                    env.getProperty("app.version"),
                    networkInfo.getFirst(),
                    networkInfo.getSecond().orElse(null),
                    blockHash.orElse(null),
                    System.currentTimeMillis(),
                    Hex.toHexString(config.nodeId()),
                    serverPort,
                    isPrivateNetwork,
                    env.getProperty("portCheckerUrl"),
                    config.bindIp(),
                    isContractsFeatureEnabled
            ));

            final String ANSI_RESET = "\u001B[0m";
            final String ANSI_BLUE = "\u001B[34m";
            System.out.println("EthereumJ database dir location: " + systemProperties.databaseDir());
            System.out.println("EthereumJ keystore dir location: " + keystore.getKeyStoreLocation());
            System.out.println(ANSI_BLUE + "Server started at http://localhost:" + serverPort + "" + ANSI_RESET);

            if (!config.getConfig().hasPath("logs.keepStdOut") || !config.getConfig().getBoolean("logs.keepStdOut")) {
                createLogAppenderForMessaging();
            }
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

        File dbDir = new File(config.databaseDir());
        machineInfo.set(new MachineInfoDTO(
                ((Double) (bean.getProcessCpuLoad() * 100)).intValue(),
                Runtime.getRuntime().freeMemory(),
                Runtime.getRuntime().maxMemory(),
                getDbDirSize(dbDir),
                getFreeDiskSpace(dbDir)
        ));

        clientMessageService.sendToTopic("/topic/machineInfo", machineInfo.get());
    }

    @Scheduled(fixedRate = 2000)
    private void doUpdateBlockchainStatus() {
        // update sync status
        syncStatus = syncManager.isSyncDone() ? SyncStatus.SHORT_SYNC : SyncStatus.LONG_SYNC;

        final Block bestBlock = ethereum.getBlockchain().getBestBlock();

        blockchainInfo.set(
                new BlockchainInfoDTO(
                        syncManager.getLastKnownBlockNumber(),
                        bestBlock.getNumber(),
                        bestBlock.getTimestamp(),
                        bestBlock.getTransactionsList().size(),
                        bestBlock.getDifficultyBI().longValue(),
                        0l, // not implemented
                        calculateHashRate(calculateAvgDifficulty(true)).longValue(),
                        gasPriceTracker.getRecommendedGasPrice(),
                        NetworkInfoDTO.SyncStatusDTO.instanceOf(syncManager.getSyncStatus())
                )
        );

        clientMessageService.sendToTopic("/topic/blockchainInfo", blockchainInfo.get());
    }

    @Scheduled(fixedRate = 2000)
    private void doUpdateNetworkInfo() {
        final NetworkInfoDTO info = new NetworkInfoDTO(
                channelManager.getActivePeers().size(),
                NetworkInfoDTO.SyncStatusDTO.instanceOf(syncManager.getSyncStatus()),
                privateMinerService.getStatus().toString(),
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
                .collect(toList());
        info.getMiners().addAll(minersList);

        networkInfo.set(info);

        clientMessageService.sendToTopic("/topic/networkInfo", info);
    }

    /**
     * Calculates average difficulty of blocks in lastBlocksForHashRate
     * Ignores first block if there are more than 2 blocks
     * @param includeUncles if set to true, difficulty of its uncles is added to each block difficulty
     * @return average block difficulty
     */
    private BigInteger calculateAvgDifficulty(boolean includeUncles) {
        final List<Block> blocks = Arrays.asList(lastBlocksForHashRate.toArray(new Block[0]));

        if (blocks.isEmpty()) {
            return BigInteger.ZERO;
        }

        if (blocks.size() == 1) {
            return blocks.get(0).getDifficultyBI();
        }

        // Calculating sum of difficulties for blocks [1, last]
        BigInteger sumDifficulties = BigInteger.ZERO;
        for (int i = 1; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            sumDifficulties = sumDifficulties.add(block.getDifficultyBI());
            if (includeUncles) {
                for (BlockHeader uncle : block.getUncleList()) {
                    sumDifficulties = sumDifficulties.add(uncle.getDifficultyBI());
                }
            }
        }

        return new BigDecimal(sumDifficulties)
                .divide(BigDecimal.valueOf(blocks.size() - 1), RoundingMode.FLOOR)
                .toBigInteger();
    }

    /**
     * Calculates block hash rate for blocks in lastBlocksForHashRate
     * Uses only blocks from 2nd to last. 1st block is used only to calculate 2nd block mining time.
     * @param blockDifficulty   Average difficulty for blocks [1, last] of lastBlocksForHashRate
     * @return Average hash rate / second
     */
    private BigInteger calculateHashRate(BigInteger blockDifficulty) {
        final List<Block> blocks = Arrays.asList(lastBlocksForHashRate.toArray(new Block[0]));

        if (blocks.size() < 2) {
            return BigInteger.ZERO;
        }

        final Block firstBlock = blocks.get(0);
        final Block bestBlock = blocks.get(blocks.size() - 1);
        // Average block time for blocks [1, last]
        float avgTime = ((float) (bestBlock.getTimestamp() - firstBlock.getTimestamp()) / (blocks.size() - 1));

        if (avgTime > 0) {
            return new BigDecimal(blockDifficulty)
                    .divide(new BigDecimal(avgTime), RoundingMode.FLOOR).toBigInteger(); // avg block difficulty / avg block seconds
        } else {
            return BigInteger.ZERO;
        }
    }

    /**
     * Measuring occupied space of database directory.
     * Caching is enabled, result updates no more than
     * once in {@link #DB_SIZE_CACHE_EVICT_MS} milliseconds.
     * @param dbDir   Database directory
     */
    private long getDbDirSize(File dbDir) {
        if ((System.currentTimeMillis() - dbSizeMeasurementTime) < DB_SIZE_CACHE_EVICT_MS) {
            return dbSize;
        }
        this.dbSizeMeasurementTime = System.currentTimeMillis();
        try (Stream<Path> paths = Files.walk(dbDir.toPath())) {
            this.dbSize = paths
                    .filter(p -> p.toFile().isFile())
                    .mapToLong(p -> p.toFile().length())
                    .sum();
        } catch (IOException e) {
            log.error("Unable to calculate db size", e);
        }

        return dbSize;
    }

    /**
     * Get free space of disk where currentDir is located.
     * Verified on Mac/Linux including symlinks.
     * @param currentDir   Directory on measured disk
     */
    private long getFreeDiskSpace(File currentDir) {
        return currentDir.getUsableSpace();
    }

    /**
     * 1. Create log appender, which will subscribe to loggers, we are interested in.
     * Appender will send logs to messaging topic then (for delivering to client side).
     *
     * 2. Stop throwing INFO logs to STDOUT, but only throw ERRORs there.
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

        final Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Optional.ofNullable(root.getAppender("STDOUT"))
            .ifPresent(stdout -> {
                stdout.stop();
                stdout.clearAllFilters();

                ThresholdFilter filter = new ThresholdFilter();
                filter.setLevel(Level.ERROR.toString());
                stdout.addFilter(filter);
                filter.start();
                stdout.start();
            });


        final ThresholdFilter filter = new ThresholdFilter();
        filter.setLevel(Level.INFO.toString());
        messagingAppender.addFilter(filter); // No effect of this
        messagingAppender.setName("ClientMessagingAppender");
        messagingAppender.setContext(context);

        root.addAppender(messagingAppender);
        filter.start();
        messagingAppender.start();
    }

    @Scheduled(fixedRate = 5000)
    public void dumpOpenFiles() {
        ProcessUtils.dumpOpenFiles();
    }

    public String getConfigDump() {
        return systemProperties.dump();
    }

    public String getGenesisDump() {
        return systemProperties.getGenesis().toString();
    }

    /**
     * Created by Stan Reshetnyk on 09.08.16.
     */
    public enum SyncStatus {
        DISABLED,
        LONG_SYNC,
        SHORT_SYNC
    }
}
