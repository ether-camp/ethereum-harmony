package com.ethercamp.harmony.config;

import com.ethercamp.harmony.web.controller.JsonRpcController;
import com.googlecode.jsonrpc4j.spring.JsonServiceExporter;
import org.ethereum.jsonrpc.JsonRpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * Created by Stan Reshetnyk on 18.07.16.
 */
@Configuration
public class ApplicationConfig extends WebMvcConfigurerAdapter {

    @Bean
    JsonRpcController myService() {
        return new JsonRpcController();
    }

    @Bean(name = "/jr")
    public JsonServiceExporter jr() {
        JsonServiceExporter ret = new JsonServiceExporter();
        ret.setService(myService());
        ret.setServiceInterface(JsonRpc.class);
        return ret;
    }

}
