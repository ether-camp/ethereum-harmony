package com.ethercamp.harmony;

import com.ethercamp.harmony.jsonrpc.JsonRpcImpl;
import com.ethercamp.harmony.service.JsonRpcUsageService;
import org.ethereum.config.DefaultConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        ConfigurableApplicationContext context =
//                SpringApplication.run(Application.class, args);
//
                SpringApplication.run(new Object[]{DefaultConfig.class, Application.class}, args);
        JsonRpcImpl rpc = context.getBean(JsonRpcUsageService.class);
    }
}
