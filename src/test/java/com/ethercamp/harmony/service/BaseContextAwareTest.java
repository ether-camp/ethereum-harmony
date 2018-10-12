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

import com.ethercamp.contrdata.config.ContractDataConfig;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import org.ethereum.core.Repository;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.inmem.HashMapDB;
import org.ethereum.db.ContractDetails;
import org.ethereum.util.blockchain.StandaloneBlockchain;
import org.ethereum.vm.DataWord;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

/**
 * Created by Stan Reshetnyk on 17.01.17.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = {
        ContractDataConfig.class, BaseContextAwareTest.TestConfig.class
})

@Ignore
public class BaseContextAwareTest {

    @Autowired
    protected StandaloneBlockchain blockchain;

    @Configuration
//    @Import({EthereumHarmonyConfig.class})
//    @ComponentScan(basePackages = "com.ethercamp.harmony", lazyInit = true)
    public static class TestConfig {

        @Bean
        public StandaloneBlockchain localBlockchain() {
            return new StandaloneBlockchain();
        }

        @Bean
        public Repository repository() {
            return localBlockchain().getBlockchain().getRepository();
        }

        @Bean
        public Storage storage() {
            Repository repository = repository();
            return new HashMapStorage(repository, storageDictionaryDb());
        }

        @Bean
        public DbSource<byte[]> storageDict() {
            return new HashMapDB();
        }

        @Bean
        public StorageDictionaryDb storageDictionaryDb() {
            StorageDictionaryDb db = new StorageDictionaryDb(storageDict());
            return db;
        }
    }

    public static class HashMapStorage implements Storage {

        private final Repository repository;
        private final StorageDictionaryDb storageDictionaryDb;

        public HashMapStorage(Repository repository, StorageDictionaryDb storageDictionaryDb) {
            this.repository = repository;
            this.storageDictionaryDb = storageDictionaryDb;
        }

        @Override
        public int size(byte[] address) {
            return keys(address).size();
//            return repository.getContractDetails(address).getStorageSize();
        }

        @Override
        public Map<DataWord, DataWord> entries(byte[] address, List<DataWord> keys) {
            keys.stream()
                    .collect(toMap(Function.identity(), Function.identity()));
            ContractDetails contractDetails = repository.getContractDetails(address);
            Map<DataWord, DataWord> result = new HashMap<>();
            keys.forEach(k -> result.put(k, contractDetails.get(k)));
            return result;
//            .getStorage(keys);
        }

        @Override
        public Set<DataWord> keys(byte[] address) {
//            return repository.getContractDetails(address).getStorageKeys();
            StorageDictionary storageDictionary = storageDictionaryDb.getDictionaryFor(Layout.Lang.solidity, address);
            if (storageDictionary == null) {
                return Collections.emptySet();
            }
            StorageDictionary.PathElement rootElement = storageDictionary.getByPath();
            return findKeysIn(address, rootElement, new HashSet<>());
        }

        // stack overflow may occur
        private Set<DataWord> findKeysIn(byte[] address, StorageDictionary.PathElement rootElement, HashSet<DataWord> dataWords) {
            rootElement.getChildren().forEach(element -> {
                if (element.hasChildren()) {
                    findKeysIn(address, element, dataWords);
                } else {
                    DataWord key = DataWord.of(element.storageKey);
                    if (repository.getStorageValue(address, key) != null) {
                        dataWords.add(key);
                    }
                }
            });
            return dataWords;
        }


        @Override
        public DataWord get(byte[] address, DataWord key) {
            return repository.getContractDetails(address).get(key);
        }
    }

    public static class HashMapDBExt extends HashMapDB {
        public Map source() {
            return storage;
        }
    }
}
