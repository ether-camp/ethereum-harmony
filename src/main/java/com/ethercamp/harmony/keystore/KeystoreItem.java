package com.ethercamp.harmony.keystore;

import org.codehaus.jackson.annotate.JsonSetter;

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
