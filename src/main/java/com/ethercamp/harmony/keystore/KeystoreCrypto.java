package com.ethercamp.harmony.keystore;

public class KeystoreCrypto {
    private String cipher;
    private String ciphertext;
    private String kdf;
    private String mac;
    private CipherParams cipherparams;
    private KdfParams kdfparams;

    public KeystoreCrypto() {
        this(null, null, null, null, null, null);
    }

    public KeystoreCrypto(String cipher, String ciphertext, String kdf, String mac, CipherParams cipherparams, KdfParams kdfparams) {
        this.cipher = cipher;
        this.ciphertext = ciphertext;
        this.kdf = kdf;
        this.mac = mac;
        this.cipherparams = cipherparams;
        this.kdfparams = kdfparams;
    }

    public String getCipher() {
        return cipher;
    }

    public void setCipher(String cipher) {
        this.cipher = cipher;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    public String getKdf() {
        return kdf;
    }

    public void setKdf(String kdf) {
        this.kdf = kdf;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public CipherParams getCipherparams() {
        return cipherparams;
    }

    public void setCipherparams(CipherParams cipherparams) {
        this.cipherparams = cipherparams;
    }

    public KdfParams getKdfparams() {
        return kdfparams;
    }

    public void setKdfparams(KdfParams kdfparams) {
        this.kdfparams = kdfparams;
    }
}
