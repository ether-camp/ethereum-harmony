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

import com.ethercamp.harmony.service.contracts.ContractsService;
import com.ethercamp.harmony.service.contracts.ContractsServiceImpl;
import com.ethercamp.harmony.service.contracts.DisabledContractService;
import org.apache.catalina.connector.Connector;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.config.CommonConfig;
import org.ethereum.config.NoAutoscan;
import org.ethereum.config.SystemProperties;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.leveldb.LevelDbDataSource;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Override default EthereumJ config to apply custom configuration.
 * This is entry point for starting EthereumJ core beans.
 *
 * Created by Stan Reshetnyk on 08.09.16.
 */
@Configuration
@ComponentScan(
        basePackages = "org.ethereum",
        excludeFilters = @ComponentScan.Filter(NoAutoscan.class))
public class EthereumHarmonyConfig extends CommonConfig {

    @Bean
    ContractsService contractsService() {
        if (harmonyProperties(systemProperties()).isContractStorageEnabled()) {
            return new ContractsServiceImpl();
        } else {
            return new DisabledContractService(contractSettingsStorage());
        }
    }

    @Bean("contractSettingsStorage")
    DbSource<byte[]> contractSettingsStorage() {
        DbSource<byte[]> settingsStorage = new LevelDbDataSource("settings");
        settingsStorage.init();

        return settingsStorage;
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
        HarmonyProperties props = harmonyProperties(systemProperties());
        LinkedHashSet<Integer> ports = new LinkedHashSet<>();

        if (props.isWebEnabled() && props.webPort() != null) {
            ports.add(props.webPort());
        }

        if (props.isRpcEnabled() && props.rpcPort() != null) {
            ports.add(props.rpcPort());
        }


        // fallback
        if (ports.isEmpty()) {
            String serverPort = System.getProperty("server.port");
            if (serverPort != null) {
                ports.add(Integer.valueOf(serverPort));
            } else {
                ports.add(8080);
            }
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
}
