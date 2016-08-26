package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigInteger;

/**
 * Created by Stan Reshetnyk on 25.08.16.
 */
@Value
@AllArgsConstructor
public class WalletConfirmTransactionDTO {

    private final String hash;

    private final BigInteger amount;

    private final boolean sending;
}
