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

import com.ethercamp.harmony.web.filter.JsonRpcUsageFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.web.filter.HiddenHttpMethodFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import com.ethercamp.harmony.util.exception.Web3jSafeAnnotationsErrorResolver;

/**
 * Created by Stan Reshetnyk on 18.07.16.
 */
@Configuration
@ComponentScan("com.ethercamp")
public class ApplicationConfig extends WebMvcConfigurerAdapter {

    private static final String ERROR_RESOLVER_KEY = "jsonrpc.web3jCompliantError";

    /**
     * Export bean which will find our json-rpc bean with @JsonRpcService and publish it.
     * https://github.com/briandilley/jsonrpc4j/issues/69
     */
    @Bean
    @Conditional(RpcEnabledCondition.class)
    @SuppressWarnings({"unchecked", "deprecation"})
    // full class path to avoid deprecation warning
    public com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceExporter exporter() {
        com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceExporter serviceExporter = new com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceExporter();

        if ("true".equalsIgnoreCase(System.getProperty(ERROR_RESOLVER_KEY, ""))) {
          serviceExporter.setErrorResolver(Web3jSafeAnnotationsErrorResolver.INSTANCE);
        }

        return serviceExporter;
    }

    /**
     * With this code we aren't required to pass explicit "Content-Type: application/json" in curl.
     * Found at https://github.com/spring-projects/spring-boot/issues/4782
     */
    @Bean
    @Conditional(RpcEnabledCondition.class)
    @SuppressWarnings("deprecation")
    // full class path to avoid deprecation warning
    public org.springframework.boot.context.embedded.FilterRegistrationBean registration(HiddenHttpMethodFilter filter) {
        org.springframework.boot.context.embedded.FilterRegistrationBean registration = new org.springframework.boot.context.embedded.FilterRegistrationBean(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Configuration of filter which gathers JSON-RPC invocation stats.
     */
    @Bean
    @Conditional(RpcEnabledCondition.class)
    public JsonRpcUsageFilter rpcUsageFilter() {
        return new JsonRpcUsageFilter();
    }

    /**
     * Configuration of filter which rejects requests to a service called on the wrong port
     */
    @Bean
    public ModulePortFilter modulePortFilter() {
        return new ModulePortFilter(
                HarmonyProperties.DEFAULT.isRpcEnabled() ? HarmonyProperties.DEFAULT.rpcPort() : null,
                HarmonyProperties.DEFAULT.isWebEnabled() ? HarmonyProperties.DEFAULT.webPort() : null
        );
    }

    // TODO: Remove resource handlers for any resources if web is not enabled
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!registry.hasMappingForPattern("/webjars/**")) {
            registry.addResourceHandler("/webjars/**").addResourceLocations(
                    "classpath:/META-INF/resources/webjars/");
        }
    }

    public static PropertySourcesPlaceholderConfigurer propertyConfigIn() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurerAdapter() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**");
            }
        };
    }
}
