package com.ethercamp.harmony.domain;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 12.07.16.
 */
@Value
@AllArgsConstructor
public class BlockchainInfoDTO {

    private Long lastBlockNumber;

    /**
     * UTC time in seconds
     */
    private Long lastBlockTime;

    private Integer lastBlockTransactions;

    private Long difficulty;

    // Not used now
    private Long lastReforkTime;

    private Long networkHashRate;
}
