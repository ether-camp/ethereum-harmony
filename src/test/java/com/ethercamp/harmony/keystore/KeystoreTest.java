package com.ethercamp.harmony.keystore;

import org.ethereum.crypto.ECKey;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import static org.ethereum.crypto.HashUtil.sha3;

/**
 * Created by Stan Reshetnyk on 27.07.16.
 */
public class KeystoreTest {

    KeystoreManager keystoreManager = new KeystoreManager();



    @Test
    public void encodeDecode() throws Exception {
        final String password = "DFGHJ$%^&";

        ECKey key = ECKey.fromPrivate(sha3(password.getBytes()));

        keystoreManager.storeKey(key, password);

        keystoreManager.loadStoredKey(Hex.toHexString(key.getAddress()), password);


    }
}
