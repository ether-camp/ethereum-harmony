package com.ethercamp.harmony.service;

import com.ethercamp.harmony.dto.WalletAddressDTO;
import com.ethercamp.harmony.dto.WalletConfirmTransactionDTO;
import com.ethercamp.harmony.dto.WalletInfoDTO;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.core.*;
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
@Slf4j(topic = "wallet")
public class WalletService {

    // TODO describe
    private static BigInteger BASE_OFFSET = new BigInteger("1000000000", 10);


    @Autowired
    Ethereum ethereum;

    @Autowired
    Repository repository;

    @Autowired
    ClientMessageService clientMessageService;

    Map<String, String> addresses = new HashMap<>();

    final List<String> pendingTransactions = Collections.synchronizedList(new ArrayList<>());

    @PostConstruct
    public void init() {
        addresses.put("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", "Default");

        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onBlock(BlockSummary blockSummary) {
                log.info("onBlock");

                List<TransactionReceipt> confirmedTransactions = blockSummary.getReceipts().stream()
                        .filter(receipt ->
                                pendingTransactions.stream().anyMatch(txHash -> txHash.equals(Hex.toHexString(receipt.getTransaction().getHash())))
                        )
                        .collect(Collectors.toList());

                if (confirmedTransactions.size() > 0) {
                    clientMessageService.sendToTopic("/topic/getWalletInfo", getWalletInfo());
                }

                confirmedTransactions.forEach(receipt -> {
                    String hash = Hex.toHexString(receipt.getTransaction().getHash());
                    BigInteger amount = ByteUtil.bytesToBigInteger(receipt.getTransaction().getValue());

                    clientMessageService.sendToTopic("/topic/confirmTransaction", new WalletConfirmTransactionDTO(
                            hash,
                            amount.divide(BASE_OFFSET).longValue()
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
                    return new WalletAddressDTO(e.getValue(), e.getKey(), balance.longValue());
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

    public void generateAddress(String name) {
        addresses.put("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", "Default");
    }

    public void trackTransaction(String txHash) {
        pendingTransactions.add(txHash);
    }
}
