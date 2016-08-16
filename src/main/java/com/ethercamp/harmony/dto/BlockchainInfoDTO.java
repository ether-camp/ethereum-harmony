package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 12.07.16.
 */
@Value
@AllArgsConstructor
public class BlockchainInfoDTO {

    private final Long highestBlockNumber;

    private final Long lastBlockNumber;

    /**
     * UTC time in seconds
     */
    private final Long lastBlockTime;

    private final Integer lastBlockTransactions;

    private final Long difficulty;

    // Not used now
    private final Long lastReforkTime;

    private final Long networkHashRate;

    private final Long gasPrice;
}
