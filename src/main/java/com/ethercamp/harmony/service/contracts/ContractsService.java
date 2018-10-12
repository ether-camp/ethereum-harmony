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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ContractsService {

    boolean deleteContract(String address);

    ContractObjects.ContractInfoDTO addContract(String address, String src) throws Exception;

    List<ContractObjects.ContractInfoDTO> getContracts();

    ContractObjects.ContractInfoDTO uploadContract(String address, MultipartFile[] files) throws Exception;

    ContractObjects.IndexStatusDTO getIndexStatus() throws Exception;

    /**
     * Get contract storage entries.
     *
     * @param hexAddress - address of contract
     * @param path - nested level of fields
     * @param pageable - for paging
     */
    Page<StorageEntry> getContractStorage(String hexAddress, String path, Pageable pageable);

    boolean importContractFromExplorer(String hexAddress) throws Exception;

    /**
     * For testing purpose.
     */
    void clearContractStorage(String hexAddress) throws Exception;
}
