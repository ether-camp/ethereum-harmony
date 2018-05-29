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
import org.ethereum.facade.SyncStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stan Reshetnyk on 09.08.16.
 */
@Value
@AllArgsConstructor
public class NetworkInfoDTO {

    private final Integer activePeers;

    private final SyncStatusDTO syncStatus;

    private final String mineStatus;

    private final Integer ethPort;

    private final Boolean ethAccessible;

    private final List<MinerDTO> miners = new ArrayList();

    @Value
    @AllArgsConstructor
    public static class SyncStatusDTO {

        private final org.ethereum.facade.SyncStatus.SyncStage stage;
        private final long curCnt;
        private final long knownCnt;
        private final long blockLastImported;
        private final long blockBestKnown;

        public static SyncStatusDTO instanceOf(SyncStatus status) {
            return new SyncStatusDTO(status.getStage(), status.getCurCnt(), status.getKnownCnt(),
                    status.getBlockLastImported(), status.getBlockBestKnown());
        }
    }
}


