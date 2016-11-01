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

import com.ethercamp.harmony.dto.ContractObjects.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.Repository;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by Stan Reshetnyk on 25.10.16.
 */
public class ContractsTests {

    ContractsService contractsService;

    private static final String ADDRESS = "0C37520af9B346D413d90805E86064B47642478E".toLowerCase();

    private static final String SRC = "" +
            "pragma solidity ^0.4.3;" +
            "contract Foo {\n" +
            "\n" +
            "        uint32 idCounter = 1;\n" +
            "        bytes32 public lastError;\n" +
            "\n" +
            "        //  function bar(uint[2] xy) {}\n" +
            "        function baz(uint32 x, bool y) returns(bool r) {\n" +
            "                        r = x > 32 || y;\n" +
            "                }\n" +
            "                //  function sam(bytes name, bool z, uint[] data) {}\n" +
            "        function sam(bytes strAsBytes, bool someFlag, string str) {}\n" +
            "}";

    //private static final String CODE = "606060405260e060020a600035046329f0de3f8114602e57806390a16130146036578063cdcd77c01460bb575b005b60dd60015481565b608060206004803580820135601f810184900490930284016040526060838152602c949293602493919284019181908382808284375050604080516020604435808b0135601f81018390048302840183019094528383529799893599909860649850929650919091019350909150819084018382808284375050505050505050505050565b60dd600435602435600060208363ffffffff16118060d65750815b9392505050565b6060908152602090f3";
    private static final String CODE = "112233";

    @Before
    public void before() {
        contractsService = new ContractsService();
        contractsService.ethereum = mock(Ethereum.class);

        contractsService.contractsStorage = new HashMapDB();
        Repository repository = mock(Repository.class);

        when(repository.getCode(any())).thenReturn(Hex.decode(CODE));
        when(contractsService.ethereum.getRepository()).thenReturn(repository);
    }

    @Test
    public void contracts_shouldAllowCreateAndList() throws Exception {
        final ContractInfoDTO contract = contractsService.addContract(ADDRESS, SRC);
        final List<ContractInfoDTO> contracts = contractsService.getContracts();

        assertEquals("Foo", contract.getName());
        assertEquals(ADDRESS, contract.getAddress());
        assertEquals(1, contracts.size());
        assertEquals("Foo", contracts.get(0).getName());
    }

    @Test(expected = RuntimeException.class)
    public void contracts_shouldFailCreate_whenWrongCode() throws Exception {
        contractsService.addContract(ADDRESS, "123");
    }
}

