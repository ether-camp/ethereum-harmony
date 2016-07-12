package com.ethercamp.harmony.ethereum;

import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.core.Genesis;
import org.ethereum.listener.EthereumListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.List;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.ethereum.config.SystemProperties.CONFIG;
import static org.ethereum.facade.EthereumFactory.createEthereum;

@Component
@Slf4j(topic = "general")
public class Ethereum {

    @Value("${eth.listen:true}")
    private boolean ethListen;
    private volatile boolean loadingStarted = false;

    @Delegate
    private final org.ethereum.facade.Ethereum ethereum;

    public Ethereum() {
        this.ethereum = createEthereum();
    }

    @PostConstruct
    public void initListeners() {
        if (ethListen) {
        } else {
            newSingleThreadExecutor().submit(this::start);
        }
    }

    public void start() {
        try {
            if (isBlockLoading() && !loadingStarted) {
                loadingStarted = true;
                getBlockLoader().loadBlocks();
            }
        } catch (Throwable e) {
            log.error("Ethereum FAIL: ", e);
        }
    }

    private static boolean isBlockLoading() {
        return isNotBlank(CONFIG.blocksLoader());
    }

    @PreDestroy
    public void destroy() {
        close();
    }

}
