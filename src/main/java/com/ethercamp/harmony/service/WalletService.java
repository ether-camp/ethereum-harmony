package com.ethercamp.harmony.service;

import com.ethercamp.harmony.dto.WalletAddressDTO;
import com.ethercamp.harmony.dto.WalletConfirmTransactionDTO;
import com.ethercamp.harmony.dto.WalletInfoDTO;
import com.ethercamp.harmony.keystore.Keystore;
import com.ethercamp.harmony.service.wallet.FileSystemWalletStore;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.util.ByteUtil;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Stan Reshetnyk on 24.08.16.
 */
@Service
@Slf4j(topic = "harmony")
public class WalletService {

    // TODO describe
    private static BigInteger BASE_OFFSET = new BigInteger("1000000000", 10);

    @Autowired
    Ethereum ethereum;

    @Autowired
    Repository repository;

    @Autowired
    FileSystemWalletStore fileSystemWalletStore;

    @Autowired
    ClientMessageService clientMessageService;

    final Map<String, String> addresses = new HashMap<>();

    @Autowired
    Keystore keystore;

    @PostConstruct
    public void init() {
        addresses.clear();
        fileSystemWalletStore.fromStore().stream()
                .forEach(a -> {
                    addresses.put(a, "Loaded");
                });

//        addresses.put("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", "Default");

        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onBlock(BlockSummary blockSummary) {
                final Set<ByteArrayWrapper> subscribed = addresses.keySet().stream()
                        .map(a -> new ByteArrayWrapper(Hex.decode(a)))
                        .collect(Collectors.toSet());

                final List<Transaction> confirmedTransactions = blockSummary.getReceipts().stream()
                        .map(receipt -> receipt.getTransaction())
                        .filter(transaction ->
                                setContains(subscribed, transaction.getReceiveAddress())
                                    || setContains(subscribed, transaction.getSender()))
                        .collect(Collectors.toList());

                if (!confirmedTransactions.isEmpty()) {
                    // update wallet if transactions are related to wallet addresses
                    clientMessageService.sendToTopic("/topic/getWalletInfo", getWalletInfo());
                }

                confirmedTransactions.forEach(transaction -> {
                    final String hash = toHexString(transaction.getHash());
                    final BigInteger amount = ByteUtil.bytesToBigInteger(transaction.getValue());
                    final boolean sending = setContains(subscribed, transaction.getSender());
                    log.info("Notify confirmed transaction sending:" + sending + ", amount:" + amount);

                    clientMessageService.sendToTopic("/topic/confirmTransaction", new WalletConfirmTransactionDTO(
                            hash,
                            amount,
                            sending
                    ));
                });
            }
        });
    }

    public WalletInfoDTO getWalletInfo() {
        List<WalletAddressDTO> list = addresses.entrySet().stream()
                .map(e -> {
                    byte[] address = Hex.decode(e.getKey());
                    BigInteger balance = repository.getBalance(address).divide(BASE_OFFSET);
                    return new WalletAddressDTO(e.getValue(), e.getKey(), balance.longValue(), true);
                })
                .collect(Collectors.toList());

        Long totalAmount = list.stream()
                .mapToLong(a -> a.getAmount())
                .sum();
//                .map(a -> a.getAmount())
//                .reduce(BigInteger.ZERO, (s, el) -> s.add(el));

        WalletInfoDTO result = new WalletInfoDTO(totalAmount);

        result.getAddresses().addAll(list);
        return result;
    }

    public String newAddress(String name, String password) {
        log.info("newAddress " + name);
        // generate new private key
        final ECKey key = new ECKey();
        final Account account = new Account();
        account.init(key);
        final String address = toHexString(account.getAddress());

        keystore.storeKey(key, password);
        addresses.put(address, name);

        flushWalletToDisk();

        clientMessageService.sendToTopic("/topic/getWalletInfo", getWalletInfo());

        return address;
    }

    public String importAddress(String address, String name) {
        validateAddress(address);

        addresses.put(address, name);

        flushWalletToDisk();

        clientMessageService.sendToTopic("/topic/getWalletInfo", getWalletInfo());

        return address;
    }

    private void validateAddress(String value) {
        Objects.requireNonNull(value);
        if (value.length() != 40) {
            throw new RuntimeException("Address value is invalid");
        }
        Hex.decode(value);
    }

    public void removeAddress(String address) {
        addresses.remove(address);

        flushWalletToDisk();

        clientMessageService.sendToTopic("/topic/getWalletInfo", getWalletInfo());
    }

    private String toHexString(byte[] value) {
        return value == null ? "" : Hex.toHexString(value);
    }

    private boolean setContains(Set<ByteArrayWrapper> set, byte[] value) {
        return value != null && set.contains(new ByteArrayWrapper(value));
    }

    private void flushWalletToDisk() {
        fileSystemWalletStore.toStore(addresses.keySet().stream().collect(Collectors.toList()));
    }
}
