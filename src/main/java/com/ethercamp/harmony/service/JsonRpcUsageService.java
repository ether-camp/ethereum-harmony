package com.ethercamp.harmony.service;

import com.ethercamp.harmony.dto.MethodCallDTO;
import com.ethercamp.harmony.jsonrpc.JsonRpcImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Services which is suites as endpoint for JSON-RPC requests. Does:
 *  - serving requests calls, by extending {@link JsonRpcImpl};
 *  - gathering statistics info of how many times RPC methods were called
 */
@Slf4j(topic = "jsonrpc")
@Service
public class JsonRpcUsageService extends JsonRpcImpl {

    // storage for stats. Method name to count
    private final Map<String, LongAdder> callsCounter = new ConcurrentHashMap();

    // storage for stats. Method name to time called last time in ms
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

    /**
     * Send stats to client side.
     */
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

    public void updateStats(String methodName) {
        long timeNow = System.currentTimeMillis();
        callsCounter.computeIfAbsent(methodName, k -> new LongAdder()).increment();
        callsTime.computeIfAbsent(methodName, k -> new AtomicLong()).set(timeNow);
    }

    /**
     * Sample of method which can be called.
     */
    @Override
    public String net_version() {
        return super.net_version();
    }
}
