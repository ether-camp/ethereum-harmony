package com.ethercamp.harmony.keystore;

import lombok.extern.slf4j.Slf4j;
import org.ethereum.crypto.ECKey;
import org.ethereum.net.swarm.Util;
import org.spongycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;

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

    public void storeKey(ECKey key, String password) {
        final File keysFolder = getKeyStoreLocation().toFile();
        keysFolder.mkdirs();

        final String fileName = "UTC--" + getISODate(Util.curTime()) + "--" + Hex.toHexString(key.getAddress());
        final File file = new File(keysFolder.getAbsolutePath() + "/" + fileName);
        Keystore.toKeystore(file, key, password);
    }

    /**
     * @return array of addresses in format "0x123abc..."
     */
    public String[] listStoredKeys() {
        final File dir = getKeyStoreLocation().toFile();
        return Arrays.stream(dir.listFiles())
                .filter(f -> !f.isDirectory())
                .map(f -> f.getName().split("--"))
                .filter(n -> n != null && n.length == 3)
                .map(a -> "0x" + a[2])
                .toArray(size -> new String[size]);
    }

    /**
     * @return some loaded key or None
     */
    public Optional<ECKey> loadStoredKey(String address, String password) {
        final File dir = getKeyStoreLocation().toFile();
        return Arrays.stream(dir.listFiles())
                .filter(f -> !f.isDirectory() && hasAddressInName(address, f.getName()))
                .map(f -> Keystore.fromKeystore(f, password))
                .findFirst();
    }

    private boolean hasAddressInName(String address, String fileName) {
        return fileName.indexOf("--" + address) == fileName.length() - address.length() - 2;
    }

    public boolean hasStoredKey(String address) {
        final File dir = getKeyStoreLocation().toFile();
        return Arrays.stream(dir.listFiles())
                .filter(f -> !f.isDirectory() && hasAddressInName(address, f.getName()))
                .findFirst()
                .isPresent();
    }

    private String getISODate(long milliseconds) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(tz);
        return df.format(new Date(milliseconds));
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
        } else {    // must be linux/unix
            return Paths.get(System.getProperty("user.home") + "/.ethereum/" + keystoreDir);
        }
    }
}
