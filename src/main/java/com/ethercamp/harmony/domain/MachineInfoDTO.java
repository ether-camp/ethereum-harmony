package com.ethercamp.harmony.domain;

import lombok.Data;

/**
 * Created by Stan Reshetnyk on 11.07.16.
 */
@Data
public class MachineInfoDTO {

    /**
     * Percentage 0..100
     */
    private Integer cpuUsage;

    /**
     * In bytes.
     */
    private Long memoryFree;

    /**
     * In bytes.
     */
    private Long memoryTotal;

    /**
     * In bytes.
     */
    private Long freeSpace;

    public MachineInfoDTO(Integer cpuUsage, Long memoryFree, Long memoryTotal, Long freeSpace) {
        this.cpuUsage = cpuUsage;
        this.memoryFree = memoryFree;
        this.memoryTotal = memoryTotal;
        this.freeSpace = freeSpace;
    }
}
