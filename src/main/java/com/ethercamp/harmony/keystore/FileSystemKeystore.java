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

import lombok.extern.slf4j.Slf4j;
import org.ethereum.crypto.ECKey;
import org.ethereum.net.swarm.Util;
import org.spongycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Key store manager working in user file system. Can store and load keys.
 * Comply to go-ethereum key store format.
 * https://github.com/ethereum/wiki/wiki/Web3-Secret-Storage-Definition
 *
 * Created by Stan Reshetnyk on 26.07.16.
 */
@Component
@Slf4j(topic = "keystore")
public class FileSystemKeystore implements Keystore {

    public KeystoreFormat keystoreFormat = new KeystoreFormat();

    @Override
    public void removeKey(String address) {
        getFiles().stream()
                .filter(f -> hasAddressInName(address, f))
                .findFirst()
                .ifPresent(f -> f.delete());
    }

    @Override
    public void storeKey(ECKey key, String password) throws RuntimeException {
        final String address = Hex.toHexString(key.getAddress());
        if (hasStoredKey(address)) {
            throw new RuntimeException("Keystore is already exist for address: " + address +
                    " Please remove old one first if you want to add with new password.");
        }

        final File keysFolder = getKeyStoreLocation().toFile();
        keysFolder.mkdirs();

        String content = keystoreFormat.toKeystore(key, password);
        storeRawKeystore(content, address);
    }

    @Override
    public void storeRawKeystore(String content, String address) throws RuntimeException {
        String fileName = "UTC--" + getISODate(Util.curTime()) + "--" + address;
        try {
            Files.write(getKeyStoreLocation().resolve(fileName), Arrays.asList(content));
        } catch (IOException e) {
            throw new RuntimeException("Problem storing key for address");
        }
    }

    /**
     * @return array of addresses in format "0x123abc..."
     */
    @Override
    public String[] listStoredKeys() {
        return getFiles().stream()
                .filter(f -> !f.isDirectory())
                .map(f -> f.getName().split("--"))
                .filter(n -> n != null && n.length == 3)
                .map(a -> "0x" + a[2])
                .toArray(size -> new String[size]);
    }

    /**
     * @return some loaded key or null
     */
    @Override
    public ECKey loadStoredKey(String address, String password) throws RuntimeException {
        return getFiles().stream()
                .filter(f -> hasAddressInName(address, f))
                .map(f -> {
                    try {
                        return Files.readAllLines(f.toPath())
                                .stream()
                                .collect(Collectors.joining(""));
                    } catch (IOException e) {
                        throw new RuntimeException("Problem reading keystore file for address:" + address);
                    }
                })
                .map(content -> keystoreFormat.fromKeystore(content, password))
                .findFirst()
                .orElse(null);
    }

    private boolean hasAddressInName(String address, File file) {
        return !file.isDirectory() && file.getName().toLowerCase().endsWith("--" + address.toLowerCase());
    }

    @Override
    public boolean hasStoredKey(String address) {
        return getFiles().stream()
                .filter(f -> hasAddressInName(address, f))
                .findFirst()
                .isPresent();
    }

    private List<File> getFiles() {
        final File dir = getKeyStoreLocation().toFile();
        final File[] files = dir.listFiles();
        return files != null ? Arrays.asList(files) : Collections.emptyList();
    }

    private String getISODate(long milliseconds) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm'Z'");
        df.setTimeZone(tz);
        return df.format(new Date(milliseconds));
    }

    /**
     * @return platform dependent path to Ethereum folder
     */
    protected Path getKeyStoreLocation() {
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
