package com.ethercamp.harmony.web.controller;

import com.ethercamp.harmony.dto.BlockInfo;
import com.ethercamp.harmony.dto.InitialInfoDTO;
import com.ethercamp.harmony.dto.MachineInfoDTO;
import com.ethercamp.harmony.service.MachineInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Queue;

@Controller
public class WebSocketController {

    @Autowired
    MachineInfoService machineInfoService;

    /**
     * Websocket handlers for immediate result.
     */

    @MessageMapping("/machineInfo")
    public MachineInfoDTO getMachineInfo() {
        return machineInfoService.getMachineInfo();
    }

    @MessageMapping("/initialInfo")
    public InitialInfoDTO getInitialInfo() {
        return machineInfoService.getInitialInfo();
    }

    @MessageMapping("/currentBlocks")
    public Queue<BlockInfo> getBlocks() {
        return machineInfoService.getBlocks();
    }

    @MessageMapping("/currentSystemLogs")
    public Queue<String> getSystemLogs() {
        return machineInfoService.getSystemLogs();
    }

    /**
     * Defines request mapping for all site pages.
     * As we have angular routing - we return index.html here.
     */
    @RequestMapping({"/", "/systemLog", "/peers", "/rpcUsage", "/terminal"})
    public String index() {
        return "index.html";
    }
}
