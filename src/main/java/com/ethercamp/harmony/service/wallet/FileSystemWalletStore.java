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

package com.ethercamp.harmony.service.wallet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts private key and password to json content and vise versa.
 */
@Component
@Slf4j(topic = "wallet")
public class FileSystemWalletStore {

    private static String FILE_NAME = "wallet.json";

    public void toStore(List<WalletAddressItem> addresses) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            final String content = mapper.writeValueAsString(addresses);

            getKeyStoreLocation().toFile().mkdirs();
            Files.write(getKeyStoreLocation().resolve(FILE_NAME), Arrays.asList(content));
        } catch (Exception e) {
            log.error("Problem storing data", e);
            throw new RuntimeException("Problem storing data. Message: " + e.getMessage(), e);
        }
    }

    public List<WalletAddressItem> fromStore() {
        final ObjectMapper mapper = new ObjectMapper();

        try {
            final File file = getKeyStoreLocation().resolve(FILE_NAME).toFile();

            final String content = Files.readAllLines(file.toPath())
                    .stream()
                    .collect(Collectors.joining(""));

            return mapper.readValue(content, new TypeReference<List<WalletAddressItem>>() {
            });
        } catch (NoSuchFileException e) {
            return Collections.EMPTY_LIST;
        } catch (IOException e) {
            throw new RuntimeException("Problem loading data. Message: " + e.getMessage(), e);
        }
    }

    /**
     * @return platform dependent path to Ethereum folder
     */
    protected Path getKeyStoreLocation() {
        return Paths.get("walletstore");
    }
}
