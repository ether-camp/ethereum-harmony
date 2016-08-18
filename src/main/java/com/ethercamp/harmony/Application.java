package com.ethercamp.harmony;

import org.ethereum.config.DefaultConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import({DefaultConfig.class})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(new Object[]{Application.class}, args);
    }
}
