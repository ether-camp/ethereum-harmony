package com.ethercamp.harmony.keystore;

import org.codehaus.jackson.annotate.JsonIgnore;

public class KdfParams {
    private Integer c;
    private Integer dklen;
    private String salt;
    private Integer n;
    private Integer p;
    private Integer r;


    public Integer getN() {
        return n;
    }

    public Integer getR() {
        return r;
    }

    public void setR(Integer r) {
        this.r = r;
    }

    public void setN(Integer n) {
        this.n = n;
    }

    public Integer getP() {
        return p;
    }

    public void setP(Integer p) {
        this.p = p;
    }

    @JsonIgnore
    public Integer getC() {
        return c;
    }

    public void setC(Integer c) {
        this.c = c;
    }

    public Integer getDklen() {
        return dklen;
    }

    public void setDklen(Integer dklen) {
        this.dklen = dklen;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }
}
