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
