package com.ethercamp.harmony.service.wallet;

import lombok.extern.slf4j.Slf4j;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts private key and password to json content and vise versa.
 */
@Component
@Slf4j(topic = "wallet")
public class FileSystemWalletStore {

    private static String FILE_NAME = "wallet.json";

    public void toStore(List<String> addresses) {
        try {
            final WalletItem walletItem = new WalletItem();
            walletItem.addresses.addAll(addresses);
            ObjectMapper mapper = new ObjectMapper();
            final String content = mapper.writeValueAsString(walletItem);

            getKeyStoreLocation().toFile().mkdirs();
            Files.write(getKeyStoreLocation().resolve(FILE_NAME), Arrays.asList(content));
        } catch (Exception e) {
            log.error("Problem storing data", e);
            throw new RuntimeException("Problem storing data. Message: " + e.getMessage(), e);
        }
    }

    public List<String> fromStore() {
        final ObjectMapper mapper = new ObjectMapper();

        try {
            final File file = getKeyStoreLocation().resolve(FILE_NAME).toFile();

            final String content = Files.readAllLines(file.toPath())
                    .stream()
                    .collect(Collectors.joining(""));

            return mapper.readValue(content, WalletItem.class).addresses;
        } catch (Exception e) {
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
