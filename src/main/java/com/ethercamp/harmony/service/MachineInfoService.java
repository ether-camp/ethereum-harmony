package com.ethercamp.harmony.service;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.filter.Filter;
import com.ethercamp.harmony.domain.BlockchainInfoDTO;
import com.ethercamp.harmony.domain.InitialInfoDTO;
import com.ethercamp.harmony.domain.MachineInfoDTO;
import com.ethercamp.harmony.ethereum.Ethereum;
import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.listener.EthereumListenerAdapter;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Stan Reshetnyk on 11.07.16.
 */
@Slf4j
@Service
public class MachineInfoService {

    private final int BLOCK_COUNT_FOR_HASH_RATE = 100;

    @Autowired
    Environment env;

    @Autowired
    ClientMessageService clientMessageService;

    @Autowired
    Ethereum ethereum;

    /**
     * Concurrent queue of last blocks.
     * Ethereum writes items when available.
     * Server reads items with interval.
     */
    private final Queue<Block> lastBlocksForHashRate = new ConcurrentLinkedQueue();

    private final AtomicReference<MachineInfoDTO> machineInfo = new AtomicReference<>(new MachineInfoDTO(0, 0l, 0l, 0l));

    private final AtomicReference<BlockchainInfoDTO> blockchainInfo =
            new AtomicReference<>(new BlockchainInfoDTO(0l, 0l, 0, 0l, 0l, 0l));

    private final AtomicReference<InitialInfoDTO> initialInfo = new AtomicReference<>(new InitialInfoDTO("", ""));

    @PostConstruct
    private void postConstruct() {
        // gather blocks to calculate hash rate
        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onBlock(Block block, List<TransactionReceipt> receipts) {
                lastBlocksForHashRate.add(block);
                if (lastBlocksForHashRate.size() > BLOCK_COUNT_FOR_HASH_RATE) {
                    lastBlocksForHashRate.poll();
                }
            }
        });

        initialInfo.set(new InitialInfoDTO(env.getProperty("ethereumJ.version"), env.getProperty("app.version")));

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
//        Logger logger = context.getLogger("dynamic_logger");

        PatternLayout patternLayout = new PatternLayout();
        patternLayout.setPattern("%d %-5level [%thread] %logger{35} - %msg%n");
        patternLayout.setContext(context);
        patternLayout.start();

        // Don't inherit root appender
//        logger.setAdditive(false);

        UnsynchronizedAppenderBase messagingAppender = new UnsynchronizedAppenderBase() {
            @Override
            protected void append(Object eventObject) {
                LoggingEvent event = (LoggingEvent) eventObject;
                String message = patternLayout.doLayout(event);
//                System.out.println("Added log entry " + event.getLoggerName() + " " + message);
                clientMessageService.sendToTopic("/topic/serverLog", message);
            }
        };
        LevelFilter filter = new LevelFilter();
        filter.setLevel(Level.INFO);
        messagingAppender.addFilter(filter);
        messagingAppender.start();

        // Attach appender to logger
        Arrays.asList("blockchain", "sync", "facade", "net", "general")
                .stream()
                .forEach(l -> {
                    Logger logger = context.getLogger(l);
                    logger.setLevel(Level.INFO);
                    logger.addAppender(messagingAppender);
                });

//        context.getLoggerList().stream()
//                .forEach(l -> l.addAppender(messagingAppender));
    }

    public MachineInfoDTO getMachineInfo() {
        return machineInfo.get();
    }

    @Scheduled(fixedRate = 5000)
    private void doUpdateMachineInfoStatus() {

        OperatingSystemMXBean bean = (OperatingSystemMXBean) ManagementFactory
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

        Block bestBlock = ethereum.getBlockchain().getBestBlock();
        bestBlock.getNumber();
        blockchainInfo.set(
                new BlockchainInfoDTO(
                        bestBlock.getNumber(),
                        bestBlock.getTimestamp(),
                        bestBlock.getTransactionsList().size(),
                        bestBlock.getDifficultyBI().longValue(),
                        0l,
                        calculateHashRate()
                )
        );

//        log.info("doCheckStatus " + bestBlock.getNumber() + " " + LocalDateTime.ofEpochSecond(bestBlock.getTimestamp(), 0, ZoneOffset.UTC));
//        log.info("HashRate " + calculateHashRate());

        clientMessageService.sendToTopic("/topic/blockchainInfo", blockchainInfo.get());
    }

    private long calculateHashRate() {
        List<Block> blocks = Arrays.asList(lastBlocksForHashRate.toArray(new Block[0]));

        if (blocks.isEmpty()) {
            return 0;
        }

        Block bestBlock = blocks.get(blocks.size() - 1);
        long difficulty = bestBlock.getDifficultyBI().longValue();

        long sumTimestamps = blocks.stream().mapToLong(b -> b.getTimestamp()).sum();
        return difficulty / (sumTimestamps / blocks.size() / 1000);
    }

    /**
     * Get free space of disk where project located.
     * Verified on multi disk Windows.
     * Not tested against sym links
     */
    private long getFreeDiskSpace() {
        File currentDir = new File(".");
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            log.debug(root.toAbsolutePath() + " vs current " + currentDir.getAbsolutePath());
            try {
                FileStore store = Files.getFileStore(root);

                boolean isCurrentDirBelongsToRoot = Paths.get(currentDir.getAbsolutePath()).startsWith(root.toAbsolutePath());
                if (isCurrentDirBelongsToRoot) {
                    long usableSpace = store.getUsableSpace();
                    log.debug("Disk available:" + readableFileSize(usableSpace)
                            + ", total:" + readableFileSize(store.getTotalSpace()));
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
        if(size <= 0) return "0";
        final String[] units = new String[] { "B", "kB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public InitialInfoDTO getInitialInfo() {
        return initialInfo.get();
    }
}
