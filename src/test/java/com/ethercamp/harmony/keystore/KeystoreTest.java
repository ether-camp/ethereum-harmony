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

import org.ethereum.crypto.ECKey;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        public Path getKeyStoreLocation() {
            return keystorePath;
        }
    };

    @Test
    public void encodeDecode() throws Exception {
        final String password = "123";

        // generate new random private key
        final ECKey key = new ECKey();
        final String address = Hex.toHexString(key.getAddress());

        fileSystemKeystore.removeKey(address);
        fileSystemKeystore.storeKey(key, password);

        fileSystemKeystore.loadStoredKey(address, password);

        fileSystemKeystore.removeKey(address);
    }

    @Test
    public void readCorrectKey() throws Exception {
        final String password = "123";
        final String address = "dc212a894a3575c61eadfb012c8db93923d806f5";

        fileSystemKeystore.removeKey(address);
        fileSystemKeystore.storeRawKeystore(CORRECT_KEY, address);

        final ECKey key = fileSystemKeystore.loadStoredKey(address, password);

        fileSystemKeystore.removeKey(address);

        assertNotNull(key);
    }

    @Test(expected = RuntimeException.class)
    public void readCorrectKeyWrongPassword() throws Exception {
        final String password = "1234";
        final String address = "dc212a894a3575c61eadfb012c8db93923d806f5";

        fileSystemKeystore.removeKey(address);
        fileSystemKeystore.storeRawKeystore(CORRECT_KEY, address);

        fileSystemKeystore.loadStoredKey(address, password);
    }

    @Test(expected = RuntimeException.class)
    public void importDuplicateKey() throws Exception {
        // generate new random private key
        final ECKey key = new ECKey();
        final String address = Hex.toHexString(key.getAddress());

        try {
            fileSystemKeystore.storeKey(key, address);
            fileSystemKeystore.storeKey(key, address);
        } finally {
            fileSystemKeystore.removeKey(address);
        }
    }

    private static String CORRECT_KEY = "{\"address\":\"dc212a894a3575c61eadfb012c8db93923d806f5\",\"crypto\":{\"cipher\":\"aes-128-ctr\",\"ciphertext\":\"4baa65c9e3438e28c657a3585c5b444746578a5b0f35e1816e43146a09dc9f94\",\"cipherparams\":{\"iv\":\"bca4d9a043c68a9b9d995492d29653f5\"},\"kdf\":\"scrypt\",\"kdfparams\":{\"dklen\":32,\"n\":262144,\"p\":1,\"r\":8,\"salt\":\"eadb4203d8618141268903a9c8c0ace4f45954e5c4679257b89b874f24b56ea3\"},\"mac\":\"b1b34957940158569ed129f9bb4373979c78748bdf6e33354bcc922d2a207efa\"},\"id\":\"c985b75c-01ef-49b7-b7f0-0c2db4c299bc\",\"version\":3}";
}
