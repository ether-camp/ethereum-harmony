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

package com.ethercamp.harmony.web.controller;

import com.ethercamp.harmony.config.WebEnabledCondition;
import com.ethercamp.harmony.model.dto.WalletInfoDTO;
import com.ethercamp.harmony.service.WalletService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Created by Stan Reshetnyk on 26.08.16.
 */
@RestController
@Conditional(WebEnabledCondition.class)
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
