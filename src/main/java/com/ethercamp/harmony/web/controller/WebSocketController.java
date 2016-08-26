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
