package com.ethercamp.harmony.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.ethereum.jsonrpc.JsonRpcImpl;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
public class JsonRpcController extends JsonRpcImpl {

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
