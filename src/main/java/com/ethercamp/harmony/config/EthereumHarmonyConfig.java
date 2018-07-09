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
import org.ethereum.config.CommonConfig;
import org.ethereum.config.NoAutoscan;
import org.ethereum.config.SystemProperties;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.leveldb.LevelDbDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

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
}
