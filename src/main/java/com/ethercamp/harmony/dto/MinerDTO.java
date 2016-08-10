package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 10.08.16.
 */
@Value
@AllArgsConstructor
public class MinerDTO {

    private final String address;

    private final Integer count;
}
