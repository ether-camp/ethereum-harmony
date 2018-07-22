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

import com.ethercamp.harmony.service.ClientMessageService;
import com.ethercamp.harmony.service.ClientMessageServiceDummy;
import com.ethercamp.harmony.service.ClientMessageServiceImpl;
import com.ethercamp.harmony.service.contracts.ContractsService;
import com.ethercamp.harmony.service.contracts.ContractsServiceImpl;
import com.ethercamp.harmony.service.contracts.DisabledContractService;
import com.ethercamp.harmony.web.filter.JsonRpcUsageFilter;
import org.apache.catalina.connector.Connector;
import org.ethereum.config.SystemProperties;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.leveldb.LevelDbDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

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
     * Configuration of filter which rejects requests to a web or rpc service
     * if called on the wrong port
     */
    @Bean
    public ModulePortFilter rpcModulePortFilter() {
        if (!HarmonyProperties.DEFAULT.isWebRpcOnePort()) {
            return new ModulePortFilter(HarmonyProperties.DEFAULT.rpcPort(), HarmonyProperties.DEFAULT.webPort());
        }

        return null;
    }

    @Bean
    public ClientMessageService clientMessageService() {
        if (WebEnabledCondition.matches()) {
            return new ClientMessageServiceImpl();
        } else {
            return new ClientMessageServiceDummy();
        }
    }

    @Bean("contractSettingsStorage")
    DbSource<byte[]> contractSettingsStorage() {
        DbSource<byte[]> settingsStorage = new LevelDbDataSource("settings");
        settingsStorage.init();

        return settingsStorage;
    }

    @Bean
    ContractsService contractsService() {
        if (harmonyProperties(SystemProperties.getDefault()).isContractStorageEnabled()) {
            return new ContractsServiceImpl();
        } else {
            return new DisabledContractService(contractSettingsStorage());
        }
    }

    @Bean
    HarmonyProperties harmonyProperties(SystemProperties properties) {
        return new HarmonyProperties(properties);
    }

    @Bean
    public EmbeddedServletContainerCustomizer containerCustomizer() {
        return (container -> {
            container.setPort(ports().get(0));
        });
    }

    private List<Integer> ports() {
        HarmonyProperties props = harmonyProperties(SystemProperties.getDefault());
        LinkedHashSet<Integer> ports = new LinkedHashSet<>();

        if (props.webPort() != null) {
            ports.add(props.webPort());
        }

        if (props.rpcPort() != null) {
            ports.add(props.rpcPort());
        }

        // fallback
        if (ports.isEmpty()) {
            ports.add(8080);
        }

        return new ArrayList<>(ports);
    }

    @Bean
    public EmbeddedServletContainerFactory servletContainer() {
        TomcatEmbeddedServletContainerFactory tomcat = new TomcatEmbeddedServletContainerFactory();
        Connector[] additionalConnectors = this.additionalConnector();
        if (additionalConnectors != null && additionalConnectors.length > 0) {
            tomcat.addAdditionalTomcatConnectors(additionalConnectors);
        }
        return tomcat;
    }

    private Connector[] additionalConnector() {
        List<Integer> ports = ports();
        ports.remove(0);
        if (ports().isEmpty()) {
            return null;
        }
        List<Connector> result = new ArrayList<>();
        for (Integer port : ports) {
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setScheme("http");
            connector.setPort(port);
            result.add(connector);
        }
        return result.toArray(new Connector[] {});
    }

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
