package com.ethercamp.harmony.domain;

import lombok.Data;

/**
 * Created by Stan Reshetnyk on 11.07.16.
 */
@Data
public class MachineInfoDTO {

    private Integer cpuUsage;

    private Long memoryUsage;

    private Long memoryTotal;

    private Long diskUsage;

    public MachineInfoDTO(Integer cpuUsage, Long memoryUsage, Long memoryTotal, Long diskUsage) {
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.memoryTotal = memoryTotal;
        this.diskUsage = diskUsage;
    }
}
