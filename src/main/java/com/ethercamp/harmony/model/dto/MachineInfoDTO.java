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

package com.ethercamp.harmony.model.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Created by Stan Reshetnyk on 11.07.16.
 */
@Value
@AllArgsConstructor
public class MachineInfoDTO {

    /**
     * Percentage 0..100
     */
    private final Integer cpuUsage;

    /**
     * In bytes.
     */
    private final Long memoryFree;

    /**
     * In bytes.
     */
    private final Long memoryTotal;

    /**
     * In bytes.
     */
    private final  Long dbSize;

    /**
     * In bytes.
     */
    private final  Long freeSpace;

}
