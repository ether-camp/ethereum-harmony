package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stan Reshetnyk on 09.08.16.
 */
@Value
@AllArgsConstructor
public class NetworkInfoDTO {

    private final Integer activePeers;

    private final String syncStatus;

    private final Integer ethPort;

    private final Boolean ethAccessible;

    private final List<MinerDTO> miners = new ArrayList();
}
