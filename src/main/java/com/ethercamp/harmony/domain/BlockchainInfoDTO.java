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

    private Long lastBlockTime;

    private Integer lastBlockTransactions;

    private Long difficulty;

    private Long lastReforkTime;

    private Long networkHashRate;
}
