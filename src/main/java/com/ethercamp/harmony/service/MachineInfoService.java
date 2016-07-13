package com.ethercamp.harmony.service;

import com.ethercamp.harmony.domain.BlockchainInfoDTO;
import com.ethercamp.harmony.domain.MachineInfoDTO;
import com.ethercamp.harmony.ethereum.Ethereum;
import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.core.*;
import org.ethereum.listener.EthereumListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
}
