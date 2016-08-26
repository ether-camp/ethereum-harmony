package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 24.08.16.
 */
@Value
@AllArgsConstructor
public class WalletAddressDTO {

    private final String name;

    private final String publicAddress;

    private final Long amount;

    private final boolean hasKeystoreKey;
}
