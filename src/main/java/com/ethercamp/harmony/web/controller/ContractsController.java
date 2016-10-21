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

package com.ethercamp.harmony.web.controller;

import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.harmony.dto.ContractInfoDTO;
import com.ethercamp.harmony.service.ContractsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Created by Stan Reshetnyk on 18.10.16.
 */
@RestController
public class ContractsController {

    @Autowired
    ContractsService contractsService;

    @RequestMapping("/contracts/{address}/storage")
    public Page<StorageEntry> getContractStorage(@PathVariable String address,
                                                 @RequestParam(required = false) String path,
                                                 @RequestParam(required = false, defaultValue = "0") int page,
                                                 @RequestParam(required = false, defaultValue = "5") int size) {
        return contractsService.getContractStorage(address, path, new PageRequest(page, size));
    }

    @RequestMapping(value = "/contracts/add", method = RequestMethod.POST)
    public boolean addContract(@RequestBody WatchContractDTO watchContract) {
        return contractsService.addContract(watchContract.address, watchContract.sourceCode);
    }

    @RequestMapping("/contracts/list")
    public List<ContractInfoDTO> getContracts() {
        return contractsService.getContracts();
    }

    @RequestMapping(value = "/contracts/{address}/delete", method = RequestMethod.POST)
    public boolean stopWatchingContract(@PathVariable String address) {
        return contractsService.deleteContract(address);
    }

    private static class WatchContractDTO {

        public String address;

        public String sourceCode;

    }
}
