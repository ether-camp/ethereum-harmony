package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 09.08.16.
 */
@Value
@AllArgsConstructor
public class BlockInfo {

    public final long blockNumber;

    public final String blockHash;

    public final String parentHash;

    public final long difficulty;
}
