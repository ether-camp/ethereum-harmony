package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 25.08.16.
 */
@Value
@AllArgsConstructor
public class WalletConfirmTransactionDTO {

    private final String hash;

    private final Long amount;
}
