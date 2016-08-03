package com.ethercamp.harmony.service;

import com.ethercamp.harmony.dto.MethodCallDTO;
import com.ethercamp.harmony.jsonrpc.JsonRpc;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Services which is suites as endpoint for JSON-RPC requests. Does:
 *  - serving requests calls, by extending {@link JsonRpcImpl};
 *  - gathering statistics info of how many times RPC methods were called
 */
@Service
@Slf4j(topic = "jsonrpc")
public class JsonRpcUsageService extends JsonRpcImpl {

    @Autowired
    JsonRpc jsonRpc;

    private final Map<String, CallStats> stats = new ConcurrentHashMap();



    @PostConstruct
    private void init() {
        Arrays.stream(jsonRpc.listAvailableMethods())
                .forEach(line -> {
                    String methodName = line.split(" ")[0];
                    stats.put(methodName, new CallStats(methodName, 0l, null));
                });
    }

    @Autowired
    ClientMessageService clientMessageService;

    /**
     * Send stats to client side.
     */
    @Scheduled(fixedRate = 2000)
    private void doSendRpcUsage() {
        List<MethodCallDTO> items = stats.values()
                .stream()
                .map(stat -> new MethodCallDTO(
                        stat.name,
                        stat.count.longValue(),
                        stat.lastCall.longValue(),
                        stat.lastResult.get()))
                .sorted((s1, s2) -> s1.getMethodName().compareTo(s2.getMethodName()))
                .collect(Collectors.toList());

        clientMessageService.sendToTopic("/topic/rpcUsage", items);
    }

    public void updateStats(String methodName, String result) {
        final long timeNow = System.currentTimeMillis();
        final CallStats callStats = stats.computeIfAbsent(methodName, k -> new CallStats(methodName, timeNow, result));

        callStats.count.increment();
        callStats.lastCall.set(timeNow);
        callStats.lastResult.set(result);
    }

    class CallStats {

        public String name;

        // time called last time in ms
        public LongAdder count = new LongAdder();

        public AtomicLong lastCall = new AtomicLong();

        public AtomicReference<String> lastResult = new AtomicReference<>();

        public CallStats(String name, long lastCallTime, String lastResultString) {
            this.name = name;
            lastCall.set(lastCallTime);
            lastResult.set(lastResultString);
        }
    }

}
