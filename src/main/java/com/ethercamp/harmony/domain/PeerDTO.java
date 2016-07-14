package com.ethercamp.harmony.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Setter;
import lombok.Value;

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

    private Long pingLatency;

    private Integer reputation;

    public void setCountry(String country) {
        this.country = country;
    }
}
