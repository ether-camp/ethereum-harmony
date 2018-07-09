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

import org.ethereum.config.SystemProperties;

/**
 * Harmony properties are here
 * For EthereumJ properties check {@link org.ethereum.config.SystemProperties}
 */
public class HarmonyProperties {

    private SystemProperties config;

    public HarmonyProperties(SystemProperties config) {
        this.config = config;
    }

    // TODO: defaults/validation
    // TODO: backward compatibility for rpc/web ports

    // TODO
    public boolean isWebEnabled() {
        return config.getConfig().getBoolean("modules.web.enabled");
    }

    // TODO
    public boolean isRpcEnabled() {
        return config.getConfig().getBoolean("modules.rpc.enabled");
    }

    // TODO
    public Integer rpcPort() {
        if (config.getConfig().hasPath("modules.rpc.listen.port")) {
            return config.getConfig().getInt("modules.rpc.listen.port");
        }

        return null;
    }

    // TODO
    // FIXME: rpc port = web port and both are enabled
    public Integer webPort() {
        if (config.getConfig().hasPath("modules.web.listen.port")) {
            return config.getConfig().getInt("modules.web.listen.port");
        }

        return null;
    }

    public boolean isContractStorageEnabled() {
        return config.getConfig().getBoolean("modules.contracts.enabled");
    }
}
