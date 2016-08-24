package com.ethercamp.harmony.web.controller;

import com.ethercamp.harmony.dto.BlockInfo;
import com.ethercamp.harmony.dto.InitialInfoDTO;
import com.ethercamp.harmony.dto.MachineInfoDTO;
import com.ethercamp.harmony.dto.WalletInfoDTO;
import com.ethercamp.harmony.service.BlockchainInfoService;
import com.ethercamp.harmony.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Queue;

@Controller
public class WebSocketController {

    @Autowired
    BlockchainInfoService blockchainInfoService;

    @Autowired
    WalletService walletService;

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

    @MessageMapping("/getWalletInfo")
    public WalletInfoDTO getWalletInfo() {
        return walletService.getWalletInfo();
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
