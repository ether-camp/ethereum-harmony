/*
 * Copyright 2015, 2016 Ether.Camp Inc. (US)
 * This file is part of Ethereum Harmony.
 *
 * Ethereum Harmony is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Ethereum Harmony is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Ethereum Harmony.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ethercamp.harmony.keystore;

import com.fasterxml.jackson.annotation.JsonSetter;

/**
 * Created by Stan Reshetnyk on 02.08.16.
 */
public class KeystoreItem {

    public KeystoreCrypto crypto;
    public String id;
    public Integer version;
    public String address;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public KeystoreCrypto getCrypto() {
        return crypto;
    }

    @JsonSetter("crypto")
    public void setCrypto(KeystoreCrypto crypto) {
        this.crypto = crypto;
    }

    @JsonSetter("Crypto")
    public void setCryptoOld(KeystoreCrypto crypto) {
        this.crypto = crypto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
