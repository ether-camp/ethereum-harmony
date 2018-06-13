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

import com.ethercamp.harmony.model.dto.MinerStatusDTO;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.facade.Ethereum;
import org.ethereum.mine.EthashListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;

/**
 * Created by Stan Reshetnyk on 19.08.16.
 */
@Slf4j(topic = "harmony")
@Service
public class PrivateMinerService {

    public final static String MINER_TOPIC = "/topic/mineInfo";

    @Autowired
    Environment env;

    @Autowired
    Ethereum ethereum;

    @Autowired
    SystemProperties config;

    @Autowired
    public Repository repository;

    @Autowired
    private ClientMessageService clientMessageService;

    private MineStatus status = MineStatus.DISABLED;

    @PostConstruct
    public void init() throws IOException, InterruptedException {
        final boolean isPrivateNetwork = env.getProperty("networkProfile", "").equalsIgnoreCase("private");
        log.info("isPrivateNetwork: " + isPrivateNetwork);
        ethereum.getBlockMiner().addListener(new EthashListener() {
            @Override
            public void miningStarted() {
                status = MineStatus.MINING;
                pushStatus(status);
                log.info("miningStarted");
            }

            @Override
            public void miningStopped() {
                status = MineStatus.DISABLED;
                pushStatus(status);
                log.info("miningStopped");
            }

            @Override
            public void blockMiningStarted(Block block) {
                log.info("new block mining started");
            }

            @Override
            public void blockMined(Block block) {
                if (status != MineStatus.MINING) {
                    status = MineStatus.MINING;
                }
                log.info("blockMined");
            }

            @Override
            public void blockMiningCanceled(Block block) {
                log.info("blockMiningCanceled");
            }

            @Override
            public void onDatasetUpdate(DatasetStatus datasetStatus) {
                switch (datasetStatus) {
                    case LIGHT_DATASET_GENERATE_START:
                        status = MineStatus.LIGHT_DAG_GENERATE;
                        break;
                    case FULL_DATASET_GENERATE_START:
                        status = MineStatus.FULL_DAG_GENERATE;
                        pushStatus(status);
                        break;
                    case FULL_DATASET_GENERATED:
                        status = MineStatus.DAG_GENERATED;
                        pushStatus(status);
                        break;
                    case DATASET_READY:
                        status = MineStatus.DAG_GENERATED;
                        break;
                }
                log.info("Dataset status updated: {}", datasetStatus);
            }
        });
        // WOW, how is stinks!
        // Overriding mine.start which was reset in {@link com.ethercamp.harmony.Application}
        SystemProperties.resetToDefault();
        config.overrideParams("mine.start", new Boolean(SystemProperties.getDefault().minerStart()).toString());
        if (config.minerStart() && !config.isSyncEnabled()) {
            ethereum.getBlockMiner().startMining();
        }
    }

    /**
     * Pushes status change immediately to client application
     */
    private void pushStatus(MineStatus status) {
        clientMessageService.sendToTopic(MINER_TOPIC, new MinerStatusDTO(status.toString()));
    }

    public MineStatus getStatus() {
        return status;
    }

    public enum MineStatus {
        DISABLED,
        LIGHT_DAG_GENERATE,
        FULL_DAG_GENERATE,
        DAG_GENERATED,
        MINING
    }
}
