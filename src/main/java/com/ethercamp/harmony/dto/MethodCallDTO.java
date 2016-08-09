package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 22.07.16.
 */
@Value
@AllArgsConstructor
public class MethodCallDTO {

    private final String methodName;

    private final Long count;

    private final Long lastTime;

    private final String lastResult;

    private final String curl;
}
