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

package com.ethercamp.harmony;

import com.ethercamp.harmony.config.EthereumHarmonyConfig;
import org.ethereum.Start;
import org.ethereum.config.SystemProperties;
import org.ethereum.facade.Ethereum;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;

@SpringBootApplication
@EnableScheduling
@Import({EthereumHarmonyConfig.class})
public class Application {

    /**
     * Does one of:
     * - start Harmony peer;
     * - perform action and exit on completion.
     */
    public static void main(String[] args) throws Exception {
        final List<String> actions = asList("importBlocks");

        final Optional<String> foundAction = asList(args).stream()
                .filter(arg -> actions.contains(arg))
                .findFirst();

        if (foundAction.isPresent()) {
            foundAction.ifPresent(action -> System.out.println("Performing action: " + action));
            Start.main(args);
            // system is expected to exit after action performed
        } else {
            if (!SystemProperties.getDefault().blocksLoader().equals("")) {
                SystemProperties.getDefault().setSyncEnabled(false);
                SystemProperties.getDefault().setDiscoveryEnabled(false);
            }

            ConfigurableApplicationContext context = SpringApplication.run(new Object[]{Application.class}, args);

            Ethereum ethereum = context.getBean(Ethereum.class);

            if (!SystemProperties.getDefault().blocksLoader().equals("")) {
                ethereum.getBlockLoader().loadBlocks();
            }
        }
    }
}
