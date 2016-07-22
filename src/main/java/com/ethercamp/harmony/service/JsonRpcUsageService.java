package com.ethercamp.harmony.service;

import com.ethercamp.harmony.dto.MethodCallDTO;
import com.ethercamp.harmony.jsonrpc.JsonRpcService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JsonRpcUsageService extends JsonRpcService {

    private final Map<String, LongAdder> callsCounter = new ConcurrentHashMap();

    private final Map<String, AtomicLong> callsTime = new ConcurrentHashMap();

    @PostConstruct
    private void init() {
        Arrays.asList("eth_protocolVersion", "net_version")
                .forEach(method -> {
                    callsCounter.put(method, new LongAdder());
                    callsTime.put(method, new AtomicLong(0l));
                });
    }

    @Autowired
    ClientMessageService clientMessageService;

    @Scheduled(fixedRate = 1500)
    private void doSendRpcUsage() {
        List<MethodCallDTO> items = callsCounter.keySet()
                .stream()
                .map(name -> new MethodCallDTO(
                        name,
                        callsCounter.get(name).longValue(),
                        callsTime.get(name).longValue()))
                .collect(Collectors.toList());

        clientMessageService.sendToTopic("/topic/rpcUsage", items);
    }

    @Override
    public String eth_protocolVersion() {
        log.info("JsonRpcServiceImpl.eth_protocolVersion");
        callsCounter.get("eth_protocolVersion").increment();
        callsTime.get("eth_protocolVersion").set(System.currentTimeMillis());
        return super.eth_protocolVersion();
    }

    @Override
    public String net_version() {
        log.info("JsonRpcServiceImpl.net_version");
        callsCounter.get("net_version").increment();
        callsTime.get("net_version").set(System.currentTimeMillis());
        return super.net_version();
    }
}
