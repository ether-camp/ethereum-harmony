package com.ethercamp.harmony.config;

import org.ethereum.config.CommonConfig;
import org.ethereum.config.NoAutoscan;
import org.ethereum.config.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.EnableTransactionManagement;

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
        SystemProperties props = new SystemProperties();
        props.setDataBaseDir(environment.getProperty("database.dir"));
        return props;
    }

}
