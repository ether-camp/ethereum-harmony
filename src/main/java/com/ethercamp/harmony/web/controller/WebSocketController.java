package com.ethercamp.harmony.web.controller;

import com.ethercamp.harmony.dto.InitialInfoDTO;
import com.ethercamp.harmony.dto.MachineInfoDTO;
import com.ethercamp.harmony.service.MachineInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class WebSocketController {

    @Autowired
    MachineInfoService machineInfoService;

    /**
     * Get current machine info.
     */
    @MessageMapping("/machineInfo")
    public MachineInfoDTO getMachineInfo() {
        return machineInfoService.getMachineInfo();
    }

    @MessageMapping("/initialInfo")
    public InitialInfoDTO getInitialInfo() {
        return machineInfoService.getInitialInfo();
    }

    @RequestMapping({"/", "/systemLog", "/peers", "/rpcUsage", "/terminal"})
    public String index() {
        return "index.html";
    }
}
