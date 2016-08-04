package com.ethercamp.harmony.config;

import com.ethercamp.harmony.jsonrpc.JsonRpc;
import com.ethercamp.harmony.jsonrpc.JsonRpcImpl;
import com.ethercamp.harmony.service.JsonRpcUsageService;
import com.ethercamp.harmony.util.AppConst;
import com.ethercamp.harmony.web.filter.JsonRpcUsageFilter;
import com.googlecode.jsonrpc4j.spring.JsonServiceExporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.HiddenHttpMethodFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * Created by Stan Reshetnyk on 18.07.16.
 */
@Configuration
public class ApplicationConfig extends WebMvcConfigurerAdapter {

    @Autowired
    JsonRpcUsageService jsonRpcUsageService;

    /**
     * Configuration for publishing all service methods as JSON-RPC
     */
    @Bean(name = AppConst.JSON_RPC_PATH)
    public JsonServiceExporter rpc() {
        JsonServiceExporter ret = new JsonServiceExporter();
        ret.setService(jsonRpcUsageService);
        ret.setServiceInterface(JsonRpc.class);
        return ret;
    }

    @Bean
    public JsonRpc jsonRpc() {
        return new JsonRpcImpl();
    }

    /**
     * With this code we aren't required to pass explicit "Content-Type: application/json" in curl.
     * Found at https://github.com/spring-projects/spring-boot/issues/4782
     */
    @Bean
    public FilterRegistrationBean registration(HiddenHttpMethodFilter filter) {
        FilterRegistrationBean registration = new FilterRegistrationBean(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Configuration of filter which gatheres JSON-RPC invocation stats.
     */
    @Bean
    public JsonRpcUsageFilter rpcUsageFilter() {
        return new JsonRpcUsageFilter();
    }
}
