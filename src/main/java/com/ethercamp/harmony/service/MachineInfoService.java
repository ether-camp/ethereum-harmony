package com.ethercamp.harmony.service;

import com.ethercamp.harmony.domain.MachineInfoDTO;
import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Created by Stan Reshetnyk on 11.07.16.
 */
@Slf4j
@Service
public class MachineInfoService {

    @Autowired
    ClientMessageService clientMessageService;

    private final AtomicInteger cpuUsage = new AtomicInteger(0);

    private final AtomicLong memoryFree = new AtomicLong(0);

    private final AtomicLong memoryTotal = new AtomicLong(0);

    private final AtomicLong freeSpace = new AtomicLong(0);

    public MachineInfoDTO getMachineInfo() {
        return new MachineInfoDTO(cpuUsage.get(), memoryFree.get(), memoryTotal.get(), freeSpace.get());
    }

    @Scheduled(fixedRate = 5000)
    private void doUpdateMachineInfoStatus() {

        OperatingSystemMXBean bean = (OperatingSystemMXBean) ManagementFactory
                .getOperatingSystemMXBean();

        cpuUsage.set(((Double) (bean.getSystemCpuLoad() * 100)).intValue());
        memoryFree.set(bean.getFreePhysicalMemorySize());
        memoryTotal.set(bean.getTotalPhysicalMemorySize());

        // Using free space of disk which holds database
        File currentDir = new File(".");
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            System.out.print(root.toAbsolutePath() + ": " + currentDir.getAbsolutePath());
            try {
                FileStore store = Files.getFileStore(root);

                boolean isCurrentDirBelongsToRoot = Paths.get(currentDir.getAbsolutePath()).startsWith(root.toAbsolutePath());
                if (isCurrentDirBelongsToRoot) {
                    long usableSpace = store.getUsableSpace();
                    freeSpace.set(usableSpace);
                    log.debug("Disk available:" + readableFileSize(usableSpace)
                            + ", total:" + readableFileSize(store.getTotalSpace()));
                    break;
                }
            } catch (IOException e) {
                log.error("Problem querying space: " + e.toString());
            }
        }

        clientMessageService.sendToTopic("/topic/machineInfo", getMachineInfo());
    }

    // for better logs
    public static String readableFileSize(long size) {
        if(size <= 0) return "0";
        final String[] units = new String[] { "B", "kB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
