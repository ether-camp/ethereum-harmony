package com.ethercamp.harmony.ethereum;

import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.ethereum.facade.EthereumFactory.createEthereum;

@Component
@Slf4j(topic = "general")
public class Ethereum {

    @Delegate
    private final org.ethereum.facade.Ethereum ethereum;

    public Ethereum() {
        this.ethereum = createEthereum();
    }

    public void start() {
        newSingleThreadExecutor().submit(this::start);
    }

    @PreDestroy
    public void destroy() {
        close();
    }

}
