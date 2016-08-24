package com.ethercamp.harmony.service;

import com.ethercamp.harmony.dto.WalletAddressDTO;
import com.ethercamp.harmony.dto.WalletInfoDTO;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.core.Repository;
import org.ethereum.facade.Ethereum;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Stan Reshetnyk on 24.08.16.
 */
@Service
@Slf4j(topic = "wallet")
public class WalletService {

    @Autowired
    Ethereum ethereum;

    @Autowired
    Repository repository;

    Map<String, String> addresses = new HashMap<>();

    @PostConstruct
    public void init() {
        addresses.put("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", "Default");
    }

    public WalletInfoDTO getWalletInfo() {
        BigInteger base = new BigInteger("1000000000", 10);

        List<WalletAddressDTO> list = addresses.entrySet().stream()
                .map(e -> {
                    byte[] address = Hex.decode(e.getKey());
                    BigInteger balance = repository.getBalance(address).divide(base);
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
}
