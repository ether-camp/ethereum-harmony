package com.ethercamp.harmony.ethereum;

import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.facade.Ethereum;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.ethereum.facade.EthereumFactory.createEthereum;

// Disabled for now
//@Component
//@Slf4j(topic = "general")
public class EthereumBean {

    @Delegate
    private final org.ethereum.facade.Ethereum ethereum;

    public EthereumBean() {
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
