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

package com.ethercamp.harmony.service;

import org.springframework.core.env.Environment;
import org.springframework.data.util.Pair;

import java.util.Optional;

/**
 * Created by Stan Reshetnyk on 20.01.17.
 */
public class BlockchainConsts {

    /**
     * Return pair of name and explorer url.
     */
    public static Pair<String, Optional<String>> getNetworkInfo(Environment env, String genesisHash) {
        final String networkNameKey = String.format("network.%s.networkName", genesisHash);
        final String explorerUrlKey = String.format("network.%s.explorerUrl", genesisHash);

        return Optional.ofNullable(env.getProperty(networkNameKey))
                .map(name -> Pair.of(name, Optional.ofNullable(env.getProperty(explorerUrlKey))))
                .orElse(Pair.of("Unknown network", Optional.empty()));
    }
}
