package com.ethercamp.harmony.web.controller;

import com.ethercamp.harmony.dto.WalletInfoDTO;
import com.ethercamp.harmony.service.WalletService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Created by Stan Reshetnyk on 26.08.16.
 */
@RestController
public class WalletController {

    @Autowired
    WalletService walletService;

    @MessageMapping("/getWalletInfo")
    public WalletInfoDTO getWalletInfo() {
        return walletService.getWalletInfo();
    }

    @MessageMapping("/newAddress")
    public String newAddress(NewAddressDTO data) {
        return walletService.newAddress(data.getName(), data.getSecret());
    }

    @MessageMapping("/importAddress")
    public String importAddress(ImportAddressDTO data) {
        return walletService.importAddress(data.getAddress(), data.getName());
    }

    @MessageMapping("/removeAddress")
    public void removeAddress(StringValueDTO data) {
        walletService.removeAddress(data.value);
    }

//    @MessageMapping("/generateWords")
    @RequestMapping(value = "/wallet/generateWords", method = RequestMethod.GET)
    public List<String> generateWords(@RequestParam Integer wordsCount) {
        return walletService.generateWords(wordsCount);
    }

    /**
     * Created by Stan Reshetnyk on 26.08.16.
     */
    @Data
    public static class StringValueDTO {

         public String value;
    }

    /**
     * Created by Stan Reshetnyk on 25.08.16.
     */
    @Data
    public static class ImportAddressDTO {

        private String address;

        private String name;
    }

    /**
     * Created by Stan Reshetnyk on 25.08.16.
     */
    @Data
    public static class NewAddressDTO {

        private String secret;

        private String name;
    }
}
