package com.ethercamp.harmony.keystore;

import lombok.extern.slf4j.Slf4j;
import org.ethereum.crypto.ECKey;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Optional;

/**
 * Key store manager. Can store and load keys.
 * Comply to go-ethereum key store format.
 * https://github.com/ethereum/wiki/wiki/Web3-Secret-Storage-Definition
 *
 * Created by Stan Reshetnyk on 26.07.16.
 */
@Component
@Slf4j(topic = "keystore")
public class KeystoreManager {

    public void storeKey(ECKey key) {
        File keysFolder = getKeyStoreLocation().toFile();
        keysFolder.mkdirs();

        throw new RuntimeException("Not implemented");
    }

    /**
     * @return array of addresses in format "0x123abc..."
     */
    public String[] listStoredKeys() {
        File dir = getKeyStoreLocation().toFile();
        return Arrays.stream(dir.listFiles())
                .filter(f -> !f.isDirectory())
                .map(f -> f.getName().split("--"))
                .filter(n -> n != null && n.length == 3)
                .map(a -> "0x" + a[2])
                .toArray(size -> new String[size]);
    }

    /**
     * @return loaded key or null
     */
    public Optional<ECKey> loadStoredKey(String address, String password) {
        File dir = getKeyStoreLocation().toFile();
        return Arrays.stream(dir.listFiles())
                .filter(f -> !f.isDirectory() && f.getName().indexOf(address) > -1)
                .map(f -> Keystore.fromKeystore(f, password))
                .findFirst();
    }

    /**
     * @return platform dependent path to Ethereum folder
     */
    private Path getKeyStoreLocation() {
        final String keystoreDir = "keystore";
        final String osName = System.getProperty("os.name").toLowerCase();

        if (osName.indexOf("win") >= 0) {
            return Paths.get(System.getenv("APPDATA") + "/Ethereum/" + keystoreDir);
        } else if (osName.indexOf("mac") >= 0) {
            return Paths.get(System.getProperty("user.home") + "/Library/Ethereum/" + keystoreDir);
        } else {
            // must be linux/unix
            return Paths.get(System.getProperty("user.home") + "/.ethereum/" + keystoreDir);
        }
    }
}
