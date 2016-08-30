package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigInteger;

/**
 * Created by Stan Reshetnyk on 24.08.16.
 */
@Value
@AllArgsConstructor
public class WalletAddressDTO {

    private final String name;

    private final String publicAddress;

    private final Long amount;

    private final BigInteger pendingAmount;

    private final boolean hasKeystoreKey;
}
