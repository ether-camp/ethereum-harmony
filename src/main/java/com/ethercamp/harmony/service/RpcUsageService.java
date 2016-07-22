package com.ethercamp.harmony.service;

import com.ethercamp.harmony.jsonrpc.JsonRpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Created by Stan Reshetnyk on 22.07.16.
 */
@Service
public class RpcUsageService {

    @Autowired
    JsonRpcService jsonRpcService;

    @Autowired
    ClientMessageService clientMessageService;

    @Scheduled(fixedRate = 1500)
    private void doSendRpcUsage() {


//        clientMessageService.sendToTopic("/topic/rpcUsage", null);
    }


}
