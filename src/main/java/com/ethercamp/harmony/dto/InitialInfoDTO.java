package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 13.07.16.
 */
@Value
@Builder
@AllArgsConstructor
public class InitialInfoDTO {

    private final String ethereumJVersion;

    private final String appVersion;

    private final String networkName;

    private final String genesisHash;

    private final Long serverStartTime;

    private final String nodeId;

    private final Integer rpcPort;
}
