package com.ethercamp.harmony.keystore;

import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

/**
 * Created by Stan Reshetnyk on 27.07.16.
 */
public class KeystoreTest {

    KeystoreManager keystoreManager = new KeystoreManager();

    @Test
    public void encodeDecode() throws Exception {
        final String password = "123";

        // generate new private key
        ECKey key = new ECKey();

        keystoreManager.storeKey(key, password);

        keystoreManager.loadStoredKey(Hex.toHexString(key.getAddress()), password);
    }

    @Test
    public void readCorrectKey() throws Exception {
        final String password = "123";
        final String address = "dc212a894a3575c61eadfb012c8db93923d806f5";

        keystoreManager.loadStoredKey(address, password);

//        String key = Hex.toHexString(ECKey.fromPrivate(HashUtil.sha3("cow".getBytes())).getPrivKeyBytes());
//        String tt = key + "";
    }
}
