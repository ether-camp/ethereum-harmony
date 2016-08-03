package com.ethercamp.harmony.keystore;

import org.ethereum.core.Block;
import org.ethereum.crypto.ECKey;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static org.junit.Assert.*;

/**
 * Created by Stan Reshetnyk on 27.07.16.
 */
public class KeystoreTest {

    /**
     * Keystore which uses temp dir instead of real user keystore dir
     */
    Keystore fileSystemKeystore = new FileSystemKeystore() {
        Path keystorePath = null;

        {
            try {
                keystorePath = Files.createTempDirectory("keystore");
            } catch (IOException e) {
                e.printStackTrace();
            }
            keystoreFormat = new KeystoreFormat();
        }

        @Override
        protected Path getKeyStoreLocation() {
            return keystorePath;
        }
    };

    @Test
    public void encodeDecode() throws Exception {
        final String password = "123";

        // generate new random private key
        final ECKey key = new ECKey();
        final String address = Hex.toHexString(key.getAddress());

        fileSystemKeystore.storeKey(key, password);

        fileSystemKeystore.loadStoredKey(address, password);

        fileSystemKeystore.removeKey(address);
    }

    @Test
    public void readCorrectKey() throws Exception {
        final String password = "123";
        final String address = "dc212a894a3575c61eadfb012c8db93923d806f5";

        fileSystemKeystore.storeRawKeystore(CORRECT_KEY, address);

        final Optional<ECKey> key = fileSystemKeystore.loadStoredKey(address, password);

        fileSystemKeystore.removeKey(address);

        assertTrue(key.isPresent());
    }

    @Test(expected = RuntimeException.class)
    public void readCorrectKeyWrongPassword() throws Exception {
        final String password = "1234";
        final String address = "dc212a894a3575c61eadfb012c8db93923d806f5";

        fileSystemKeystore.storeRawKeystore(CORRECT_KEY, address);

        fileSystemKeystore.loadStoredKey(address, password);
    }

    private static String CORRECT_KEY = "{\"address\":\"dc212a894a3575c61eadfb012c8db93923d806f5\",\"crypto\":{\"cipher\":\"aes-128-ctr\",\"ciphertext\":\"4baa65c9e3438e28c657a3585c5b444746578a5b0f35e1816e43146a09dc9f94\",\"cipherparams\":{\"iv\":\"bca4d9a043c68a9b9d995492d29653f5\"},\"kdf\":\"scrypt\",\"kdfparams\":{\"dklen\":32,\"n\":262144,\"p\":1,\"r\":8,\"salt\":\"eadb4203d8618141268903a9c8c0ace4f45954e5c4679257b89b874f24b56ea3\"},\"mac\":\"b1b34957940158569ed129f9bb4373979c78748bdf6e33354bcc922d2a207efa\"},\"id\":\"c985b75c-01ef-49b7-b7f0-0c2db4c299bc\",\"version\":3}";
}
