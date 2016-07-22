package com.ethercamp.harmony.web.controller;

import com.ethercamp.harmony.jsonrpc.JsonRpcService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
public class JsonRpcController extends JsonRpcService {

    @Override
    public String eth_protocolVersion() {
        log.info("JsonRpcServiceImpl.eth_protocolVersion");
        return super.eth_protocolVersion();
    }

    @Override
    public String net_version() {
        log.info("JsonRpcServiceImpl.net_version");
        return super.net_version();
    }
}
