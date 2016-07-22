package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 13.07.16.
 */
@Value
@AllArgsConstructor
public class InitialInfoDTO {

    private String ethereumJVersion;

    private String appVersion;
}
