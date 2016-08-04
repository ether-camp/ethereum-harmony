package com.ethercamp.harmony.keystore;

import org.ethereum.crypto.ECKey;

import java.util.Optional;

/**
 * Created by Stan Reshetnyk on 01.08.16.
 *
 * Each method could throw {RuntimeException}, because of access to IO and crypto functions.
 */
public interface Keystore {

    void removeKey(String address);

    void storeKey(ECKey key, String password);

    void storeRawKeystore(String content, String address);

    String[] listStoredKeys();

    ECKey loadStoredKey(String address, String password);

    boolean hasStoredKey(String address);
}
