package com.ethercamp.harmony.keystore;

public class CipherParams {
    private String iv;

    public CipherParams() {
        this(null);
    }

    public CipherParams(String iv) {
        this.iv = iv;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }
}