/*
 * Copyright 2015, 2016 Ether.Camp Inc. (US)
 * This file is part of Ethereum Harmony.
 *
 * Ethereum Harmony is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Ethereum Harmony is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Ethereum Harmony.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ethercamp.harmony.config;

import com.ethercamp.harmony.jsonrpc.JsonRpc;
import com.ethercamp.harmony.jsonrpc.JsonRpcImpl;
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
    JsonRpc jsonRpc;

    /**
     * Configuration for publishing all service methods as JSON-RPC
     */
    @Bean(name = AppConst.JSON_RPC_PATH)
    public JsonServiceExporter rpc() {
        JsonServiceExporter ret = new JsonServiceExporter();
        ret.setService(jsonRpc);
        ret.setServiceInterface(JsonRpc.class);
        return ret;
    }

    @Bean
    public JsonRpc harmonyJsonRpc() {
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
     * Configuration of filter which gathers JSON-RPC invocation stats.
     */
    @Bean
    public JsonRpcUsageFilter rpcUsageFilter() {
        return new JsonRpcUsageFilter();
    }
}
