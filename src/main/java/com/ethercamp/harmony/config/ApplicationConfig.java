package com.ethercamp.harmony.config;

import com.ethercamp.harmony.jsonrpc.AddContentTypeFilter;
import com.ethercamp.harmony.jsonrpc.JsonRpc;
import com.ethercamp.harmony.service.JsonRpcUsageService;
import com.googlecode.jsonrpc4j.spring.JsonServiceExporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import javax.servlet.Filter;

/**
 * Created by Stan Reshetnyk on 18.07.16.
 */
@Configuration
public class ApplicationConfig extends WebMvcConfigurerAdapter {

    @Autowired
    JsonRpcUsageService jsonRpcUsageService;

    @Bean(name = "/rpc")
    public JsonServiceExporter rpc() {
        JsonServiceExporter ret = new JsonServiceExporter();
        ret.setService(jsonRpcUsageService);
        ret.setServiceInterface(JsonRpc.class);
        return ret;
    }

    @Bean
    public FilterRegistrationBean filterRegistrationBean() {
        FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        Filter filter = new AddContentTypeFilter();
        registrationBean.setFilter(filter);
        registrationBean.setOrder(Integer.MIN_VALUE);
        return registrationBean;
    }
}
