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

import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.harmony.model.dto.ContractObjects;
import com.ethercamp.harmony.service.DisabledException;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.datasource.DbSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j(topic = "contracts")
public class DisabledContractService implements ContractsService {

    private final static String DISABLED_MSG = "Contracts service is disabled";

    public DisabledContractService(final DbSource<byte[]> settingsStorage) {
        log.info("Contract service is disabled");
        if (ContractsServiceImpl.isContractStorageCreated(settingsStorage)) {
            log.error("Contracts service was enabled before. If you are going to disable it now, " +
                    "your contract storage data will be corrupted due to disabled tracking of new blocks.");
            log.error("Either enable contract storage or remove following directories from DB folder: " +
                    "settings, contractsStorage, contractCreation, storageDict");
            log.error("Exiting...");
            System.exit(141);
        }
    }

    @Override
    public boolean deleteContract(String address) {
        throw new DisabledException(DISABLED_MSG);
    }

    @Override
    public ContractObjects.ContractInfoDTO addContract(String address, String src) throws Exception {
        throw new DisabledException(DISABLED_MSG);
    }

    @Override
    public List<ContractObjects.ContractInfoDTO> getContracts() {
        throw new DisabledException(DISABLED_MSG);
    }

    @Override
    public ContractObjects.ContractInfoDTO uploadContract(String address, MultipartFile[] files) throws Exception {
        throw new DisabledException(DISABLED_MSG);
    }

    @Override
    public ContractObjects.IndexStatusDTO getIndexStatus() throws Exception {
        throw new DisabledException(DISABLED_MSG);
    }

    @Override
    public Page<StorageEntry> getContractStorage(String hexAddress, String path, Pageable pageable) {
        throw new DisabledException(DISABLED_MSG);
    }

    @Override
    public boolean importContractFromExplorer(String hexAddress) throws Exception {
        throw new DisabledException(DISABLED_MSG);
    }

    @Override
    public void clearContractStorage(String hexAddress) throws Exception {
        throw new DisabledException(DISABLED_MSG);
    }
}
