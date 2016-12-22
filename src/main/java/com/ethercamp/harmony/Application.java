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
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

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

        System.getProperties().entrySet()
                .forEach(e -> System.out.println("System prop " + e.getKey() + "=" + e.getValue()));

        if (asList(args).stream().anyMatch(arg -> actions.contains(arg))) {
            Start.main(args);
        } else {
            SpringApplication.run(new Object[]{Application.class}, args);
        }
    }
}
