package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 11.07.16.
 */
@Value
@AllArgsConstructor
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

}
