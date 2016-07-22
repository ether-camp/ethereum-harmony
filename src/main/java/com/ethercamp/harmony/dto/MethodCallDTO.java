package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 22.07.16.
 */
@Value
@AllArgsConstructor
public class MethodCallDTO {

    private String methodName;

    private Long count;

    private Long lastTime;
}
