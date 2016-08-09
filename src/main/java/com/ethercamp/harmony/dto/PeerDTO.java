package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 14.07.16.
 */
@Value
@AllArgsConstructor
public class PeerDTO {

    private final String nodeId;

    private final String ip;

    // 3 letter code, used for map in UI
    private final String country3Code;

    // 2 letter code, used for flags in UI
    private final String country2Code;

    // seconds???
    private final Long lastPing;

    // ms
    private final Double pingLatency;

    private final Integer reputation;

    private final Boolean isActive;

    private final String details;

}
