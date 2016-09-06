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

import com.ethercamp.harmony.dto.*;
import com.ethercamp.harmony.service.BlockchainInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Queue;

@Controller
public class WebSocketController {

    @Autowired
    BlockchainInfoService blockchainInfoService;

    /**
     * Websocket handlers for immediate result.
     */

    @MessageMapping("/machineInfo")
    public MachineInfoDTO getMachineInfo() {
        return blockchainInfoService.getMachineInfo();
    }

    @MessageMapping("/initialInfo")
    public InitialInfoDTO getInitialInfo() {
        return blockchainInfoService.getInitialInfo();
    }

    @MessageMapping("/currentBlocks")
    public Queue<BlockInfo> getBlocks() {
        return blockchainInfoService.getBlocks();
    }

    @MessageMapping("/currentSystemLogs")
    public Queue<String> getSystemLogs() {
        return blockchainInfoService.getSystemLogs();
    }

    /**
     * Defines request mapping for all site pages.
     * As we have angular routing - we return index.html here.
     */
    @RequestMapping({"/", "/systemLog", "/peers", "/rpcUsage", "/terminal", "/wallet"})
    public String index() {
        return "index.html";
    }
}
