package com.ethercamp.harmony.web.controller;

import com.ethercamp.harmony.domain.InitialInfoDTO;
import com.ethercamp.harmony.domain.MachineInfoDTO;
import com.ethercamp.harmony.service.MachineInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

    @RequestMapping({"/", "/systemLog", "/peers"})
    public String index() {
        return "index.html";
    }
}
