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

import com.ethercamp.contrdata.storage.Storage;
import org.ethereum.facade.Repository;
import org.ethereum.vm.DataWord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class StorageImpl implements Storage {

    @Autowired
    Repository repository;



//    @Autowired
//    public void setRepository(Ethereum ethereum) {
//        this.repository = ethereum.getRepository();
//    }

    @Override
//    @Profiled
    public int size(byte[] address) {
        return repository.getStorageSize(address);
    }

    @Override
//    @Profiled
    public Map<DataWord, DataWord> entries(byte[] address, List<DataWord> keys) {
        return repository.getStorage(address, keys);
    }

    @Override
//    @Profiled
    public Set<DataWord> keys(byte[] address) {
        return repository.getStorageKeys(address);
    }

    @Override
//    @Profiled
    public DataWord get(byte[] address, DataWord key) {
        return repository.getStorageValue(address, key);
    }
}
