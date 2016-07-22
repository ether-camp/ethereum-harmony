package com.ethercamp.harmony.config;

import com.ethercamp.harmony.jsonrpc.AddContentTypeFilter;
import com.ethercamp.harmony.jsonrpc.JsonRpc;
import com.ethercamp.harmony.service.JsonRpcUsageService;
import com.ethercamp.harmony.util.AppConst;
import com.ethercamp.harmony.web.filter.JsonRpcUsageFilter;
import com.googlecode.jsonrpc4j.spring.JsonServiceExporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import javax.servlet.Filter;
import java.util.Arrays;

/**
 * Created by Stan Reshetnyk on 18.07.16.
 */
@Configuration
public class ApplicationConfig extends WebMvcConfigurerAdapter {

    @Autowired
    JsonRpcUsageService jsonRpcUsageService;

    @Bean(name = AppConst.JSON_RPC_PATH)
    public JsonServiceExporter rpc() {
        JsonServiceExporter ret = new JsonServiceExporter();
        ret.setService(jsonRpcUsageService);
        ret.setServiceInterface(JsonRpc.class);
        return ret;
    }

//    @Bean
//    public FilterRegistrationBean filterRegistrationBean() {
//        FilterRegistrationBean registrationBean = new FilterRegistrationBean();
//        Filter filter = new AddContentTypeFilter();
//        registrationBean.setFilter(filter);
//        registrationBean.setUrlPatterns(Arrays.asList(AppConst.JSON_RPC_PATH));
//        registrationBean.setOrder(Integer.MIN_VALUE);
//        return registrationBean;
//    }

    @Bean
    public JsonRpcUsageFilter rpcUsageFilter() {
        return new JsonRpcUsageFilter();
    }
}
