package com.ethercamp.harmony.service.wallet;

import lombok.NoArgsConstructor;

/**
 * Created by Stan Reshetnyk on 26.08.16.
 */
@NoArgsConstructor
public class WalletAddressItem {

    public String address;

    public String name;

    public WalletAddressItem(String address, String name) {
        this.address = address;
        this.name = name;
    }
}
