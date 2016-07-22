package com.ethercamp.harmony.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 14.07.16.
 */
@Value
@AllArgsConstructor
public class PeerDTO {

    private String nodeId;

    private String ip;

    // 3 letter code, used for map in UI
    private String country3Code;

    // 2 letter code, used for flags in UI
    private String country2Code;

    // seconds???
    private Long lastPing;

    // ms
    private Double pingLatency;

    private Integer reputation;

    private Boolean isActive;

}
