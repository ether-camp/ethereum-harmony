package com.ethercamp.harmony.config;

import org.ethereum.config.CommonConfig;
import org.ethereum.config.NoAutoscan;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Genesis;
import org.ethereum.core.genesis.GenesisLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.*;

/**
 * Override default EthereumJ config to apply custom configuration.
 * This is entry point for starting EthereumJ core beans.
 *
 * Created by Stan Reshetnyk on 08.09.16.
 */
@Configuration
@EnableTransactionManagement
@ComponentScan(
        basePackages = "org.ethereum",
        excludeFilters = @ComponentScan.Filter(NoAutoscan.class))
public class EthereumHarmonyConfig extends CommonConfig {

    @Autowired
    Environment environment;

    @Bean
    @Override
    public SystemProperties systemProperties() {
        final String genesisFile = environment.getProperty("genesisFile");

        // Override genesis loading because core doesn't allow setting absolute path
        final SystemProperties props = new SystemProperties() {
            private Genesis genesis;

            @Override
            public Genesis getGenesis() {
                if (genesis != null) {
                    return genesis;
                }
                if (genesisFile != null) {
                    try (InputStream is = new FileInputStream(new File(genesisFile))) {
                        genesis = GenesisLoader.loadGenesis(this, is);
                    } catch (Exception e) {
                        System.err.println("Genesis block configuration is corrupted or not found " + genesisFile);
                        e.printStackTrace();
                        System.exit(-1);
                    }
                } else {
                    genesis = super.getGenesis();
                }
                return genesis;
            }
        };
        props.setDataBaseDir(environment.getProperty("database.dir"));

        return props;
    }
}
