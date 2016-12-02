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
import org.ethereum.datasource.inmem.HashMapDB;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.Repository;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by Stan Reshetnyk on 25.10.16.
 */
public class ContractsTests {

    ContractsService contractsService;
    Repository repository;

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

    private static final String CODE = "112233";

    @Before
    public void before() {
        contractsService = new ContractsService();
        contractsService.ethereum = mock(Ethereum.class);

        contractsService.contractsStorage = new HashMapDB<byte[]>();
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

    @Test
    public void contracts_shouldDetectContractName_whenFileUpload() throws Exception {
        when(repository.getCode(any())).thenReturn(Hex.decode("60606040525b33600060006101000a81548173ffffffffffffffffffffffffffffffffffffffff02191690836c010000000000000000000000009081020402179055505b5b610181806100526000396000f360606040523615610044576000357c0100000000000000000000000000000000000000000000000000000000900480639937232114610086578063a6f9dae1146100a3575b6100845b60405180807f7465737400000000000000000000000000000000000000000000000000000000815260200150600401905060405180910390a05b565b005b34610000576100a160048080359060200190919050506100c0565b005b34610000576100be60048080359060200190919050506100e8565b005b6000600082604051808260001916815260200191505060405180910390a0600091505b505050565b600060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff16141561017d5780600060006101000a81548173ffffffffffffffffffffffffffffffffffffffff02191690836c010000000000000000000000009081020402179055505b5b5b5056"));

        final byte[] contract1 = ("contract owned {\n" +
                "  address owner;\n" +
                "  function owned() {\n" +
                "    owner = msg.sender;\n" +
                "  }\n" +
                "  function changeOwner(address newOwner) onlyowner {\n" +
                "    owner = newOwner;\n" +
                "  }\n" +
                "  modifier onlyowner() {\n" +
                "    if (msg.sender==owner) _;\n" +
                "  }\n" +
                "}\n").getBytes("UTF-8");

        final byte[] contract2 =  ("import \"std.sol\";" +
                "contract Contract is owned {\n" +
                "  enum E { E1, E2 }\n" +
                "  function test(bytes32 str) {\n" +
                "    log0(str);\n" +
                "    E e = E.E1;\n" +
                "    uint b;\n" +
                "  }\n" +
                "  function () payable {\n" +
                "    log0(\"test\");\n" +
                "  }\n" +
                "}\n").getBytes("UTF-8");

        MockMultipartFile[] files = {
                new MockMultipartFile("std.sol", "std.sol", "plain/text", contract1)
                ,
                new MockMultipartFile("contract.sol", "contract.sol", "plain/text", contract2)

        };
        ContractInfoDTO addedContract = contractsService.uploadContract(ADDRESS, files);

        assertThat(addedContract.getName(), is("Contract"));
    }

    @Test
    public void contracts_shouldExtractHash_1() throws Exception {
        Set<String> set = ContractsService.extractFuncHashes("PUSH1 0x60  PUSH1 0x40  MSTORE JUMPDEST CALLER PUSH1 0x0  PUSH1 0x0  PUSH2 0x100  EXP DUP2 SLOAD DUP2 PUSH20 0xffffffffffffffffffffffffffffffffffffffff  MUL NOT AND SWAP1 DUP4 PUSH13 0x1000000000000000000000000  SWAP1 DUP2 MUL DIV MUL OR SWAP1 SSTORE POP JUMPDEST JUMPDEST PUSH2 0x181  DUP1 PUSH2 0x52  PUSH1 0x0  CODECOPY PUSH1 0x0  RETURN PUSH1 0x60  PUSH1 0x40  MSTORE CALLDATASIZE ISZERO PUSH2 0x44  JUMPI PUSH1 0x0  CALLDATALOAD PUSH29 0x100000000000000000000000000000000000000000000000000000000  SWAP1 DIV DUP1 PUSH4 0x99372321  EQ PUSH2 0x86  JUMPI DUP1 PUSH4 0xa6f9dae1  EQ PUSH2 0xa3  JUMPI JUMPDEST PUSH2 0x84  JUMPDEST PUSH1 0x40  MLOAD DUP1 DUP1 PUSH32 0x7465737400000000000000000000000000000000000000000000000000000000  DUP2 MSTORE PUSH1 0x20  ADD POP PUSH1 0x4  ADD SWAP1 POP PUSH1 0x40  MLOAD DUP1 SWAP2 SUB SWAP1 LOG0 JUMPDEST JUMP JUMPDEST STOP JUMPDEST CALLVALUE PUSH2 0x0  JUMPI PUSH2 0xa1  PUSH1 0x4  DUP1 DUP1 CALLDATALOAD SWAP1 PUSH1 0x20  ADD SWAP1 SWAP2 SWAP1 POP POP PUSH2 0xc0  JUMP JUMPDEST STOP JUMPDEST CALLVALUE PUSH2 0x0  JUMPI PUSH2 0xbe  PUSH1 0x4  DUP1 DUP1 CALLDATALOAD SWAP1 PUSH1 0x20  ADD SWAP1 SWAP2 SWAP1 POP POP PUSH2 0xe8  JUMP JUMPDEST STOP JUMPDEST PUSH1 0x0  PUSH1 0x0  DUP3 PUSH1 0x40  MLOAD DUP1 DUP3 PUSH1 0x0  NOT AND DUP2 MSTORE PUSH1 0x20  ADD SWAP2 POP POP PUSH1 0x40  MLOAD DUP1 SWAP2 SUB SWAP1 LOG0 PUSH1 0x0  SWAP2 POP JUMPDEST POP POP POP JUMP JUMPDEST PUSH1 0x0  PUSH1 0x0  SWAP1 SLOAD SWAP1 PUSH2 0x100  EXP SWAP1 DIV PUSH20 0xffffffffffffffffffffffffffffffffffffffff  AND PUSH20 0xffffffffffffffffffffffffffffffffffffffff  AND CALLER PUSH20 0xffffffffffffffffffffffffffffffffffffffff  AND EQ ISZERO PUSH2 0x17d  JUMPI DUP1 PUSH1 0x0  PUSH1 0x0  PUSH2 0x100  EXP DUP2 SLOAD DUP2 PUSH20 0xffffffffffffffffffffffffffffffffffffffff  MUL NOT AND SWAP1 DUP4 PUSH13 0x1000000000000000000000000  SWAP1 DUP2 MUL DIV MUL OR SWAP1 SSTORE POP JUMPDEST JUMPDEST JUMPDEST POP JUMP");

        assertThat(set.size(), is(2));
        assertTrue(set.contains("99372321"));
        assertTrue(set.contains("a6f9dae1"));
    }

    @Test
    public void contracts_shouldDetectContractName_whenSameHashes() throws Exception {
        when(repository.getCode(any())).thenReturn(Hex.decode("6060604052346000575b6054806100166000396000f360606040526000357c01000000000000000000000000000000000000000000000000000000009004806399372321146036575b6000565b34600057604e60048080359060200190919050506050565b005b5b5056"));

        final String sourceCode =
                "contract Contract1 { function test(bytes32 str) {}}" +
                "contract Contract2 { function test(bytes32 str) {}}";
        ContractInfoDTO addedContract = contractsService.addContract(ADDRESS, sourceCode);

        assertThat(addedContract.getName(), containsString("Contract1"));
        assertThat(addedContract.getName(), containsString("Contract2"));
    }
}

