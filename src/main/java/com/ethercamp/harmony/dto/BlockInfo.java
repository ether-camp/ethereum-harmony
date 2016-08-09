package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 09.08.16.
 */
@Value
@AllArgsConstructor
public class BlockInfo {

    private final long blockNumber;

    private final String blockHash;

    private final String parentHash;

    private final long difficulty;
}
