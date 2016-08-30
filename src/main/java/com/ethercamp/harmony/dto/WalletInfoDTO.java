package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stan Reshetnyk on 24.08.16.
 */
@Value
@AllArgsConstructor
public class WalletInfoDTO {

    private final BigInteger totalAmount;

    private final List<WalletAddressDTO> addresses = new ArrayList();
}
