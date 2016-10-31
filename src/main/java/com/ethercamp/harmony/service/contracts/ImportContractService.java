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

package com.ethercamp.harmony.service.contracts;

import lombok.extern.slf4j.Slf4j;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.DetailsDataStore;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.RepositoryImpl;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.sync.SyncManager;
import org.ethereum.util.blockchain.StandaloneBlockchain;
import org.ethereum.validator.DependentBlockHeaderRuleAdapter;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Stan Reshetnyk on 24.10.16.
 */
@Component
@Slf4j(topic = "import-contract")
public class ImportContractService {

    private ExecutorService executor = Executors.newFixedThreadPool(1);

    @Autowired
    Blockchain blockchainCurrent;

    @Autowired
    SystemProperties config;

    StandaloneBlockchain standalone;

//    @PostConstruct
    public void init() {
        executor.submit(() -> startImport());
    }

    private void startImport() {
        StandaloneBlockchain bc = standalone = new StandaloneBlockchain();
        Genesis genesis = config.getGenesis();
        bc.withGenesis(config.getGenesis());

        IndexedBlockStore blockStore = new IndexedBlockStore();
        blockStore.init(new HashMapDB(), new HashMapDB());

        KeyValueDataSource detailsDS = new HashMapDB();
        KeyValueDataSource storageDS = new HashMapDB();
        KeyValueDataSource stateDS = new HashMapDB();
        DetailsDataStore detailsDataStore = new DetailsDataStore().withDb(detailsDS, storageDS);
        RepositoryImpl repository = new RepositoryImpl(detailsDataStore, stateDS, true)
                .withBlockStore(blockStore);

        ProgramInvokeFactoryImpl programInvokeFactory = new ProgramInvokeFactoryImpl();
        CompositeEthereumListener listener = new CompositeEthereumListener();

        BlockchainImpl blockchain = new BlockchainImpl(blockStore, repository)
                .withEthereumListener(listener)
                .withSyncManager(new SyncManager());
        blockchain.setParentHeaderValidator(new DependentBlockHeaderRuleAdapter());
        blockchain.setProgramInvokeFactory(programInvokeFactory);

        blockchain.byTest = true;

        PendingStateImpl pendingState = new PendingStateImpl(listener, blockchain);

        pendingState.setBlockchain(blockchain);
        blockchain.setPendingState(pendingState);

        Repository track = repository.startTracking();
        for (ByteArrayWrapper key : genesis.getPremine().keySet()) {
            track.createAccount(key.getData());
            track.addBalance(key.getData(), genesis.getPremine().get(key).getBalance());
        }
//        for (Pair<byte[], BigInteger> acc : initialBallances) {
//            track.createAccount(acc.getLeft());
//            track.addBalance(acc.getLeft(), acc.getRight());
//        }

        track.commit();
        repository.commitBlock(genesis.getHeader());

        blockStore.saveBlock(genesis, genesis.getCumulativeDifficulty(), true);

        blockchain.setBestBlock(genesis);
        blockchain.setTotalDifficulty(genesis.getCumulativeDifficulty());

        long startTime = System.currentTimeMillis();
        log.info("Block import started");
        final long bestBlockNumber = blockchainCurrent.getBestBlock().getNumber();
        for (long i = 0; i < bestBlockNumber; i++) {
            Block block = blockchainCurrent.getBlockByNumber(i);
            blockchain.add(block);
            if (i % 100 == 0) {
                log.info("Block imported " + i);
            }
        }
        log.info("Block import stopped " + (System.currentTimeMillis() - startTime));
    }
}
