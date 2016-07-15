package com.ethercamp.harmony.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Created by Stan Reshetnyk on 14.07.16.
 */
@Data
@AllArgsConstructor
public class PeerDTO {

    private String nodeId;

    private String ip;

    private String country;

    private Long lastPing;

    private Double pingLatency;

    private Integer reputation;

    public void setLastPing(Long lastPing) {
        this.lastPing = lastPing;
    }
}
