package com.ethercamp.harmony.service;

import com.ethercamp.harmony.domain.MachineInfoDTO;
import com.google.common.io.Files;
import com.sun.management.OperatingSystemMXBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Created by Stan Reshetnyk on 11.07.16.
 */
@Service
public class MachineInfoService {

    @Autowired
    ClientMessageService clientMessageService;

    private final AtomicInteger cpuUsage = new AtomicInteger(0);

    private final AtomicLong memoryUsage = new AtomicLong(1);

    private final AtomicLong memoryTotal = new AtomicLong(2);

    private final AtomicLong diskUsage = new AtomicLong(3);

    public MachineInfoDTO getMachineInfo() {
        return new MachineInfoDTO(cpuUsage.get(), memoryUsage.get(), memoryTotal.get(), diskUsage.get());
    }

    @Scheduled(fixedRate = 5000)
    private void doUpdateMachineInfoStatus() {

        OperatingSystemMXBean bean = (OperatingSystemMXBean) ManagementFactory
                .getOperatingSystemMXBean();

        cpuUsage.set(((Double) (bean.getProcessCpuLoad() * 100)).intValue());
        memoryUsage.set(bean.getFreePhysicalMemorySize());
        memoryTotal.set(bean.getTotalPhysicalMemorySize());

        File currentDir = new File("."); // eventually will be changed to database dir
        Iterable<File> files = Files.fileTreeTraverser().breadthFirstTraversal(currentDir);
        final Long sizeOnDisk = toStream( files ).mapToLong( File::length ).sum();
        diskUsage.set(sizeOnDisk);

        clientMessageService.sendToTopic("/topic/machineInfo", getMachineInfo());
    }

    private Stream<File> toStream(Iterable<File> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }
}
