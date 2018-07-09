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

import com.ethercamp.harmony.model.dto.ContractObjects.*;
import com.ethercamp.harmony.util.SolcUtils;
import org.apache.commons.io.IOUtils;
import org.ethereum.datasource.inmem.HashMapDB;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.Repository;
import org.ethereum.solidity.compiler.CompilationResult;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by Stan Reshetnyk on 25.10.16.
 */
public class ContractsTests {

    ContractsServiceImpl contractsService;
    Repository repository;

    private static final String ADDRESS = "0C37520af9B346D413d90805E86064B47642478E".toLowerCase();

    private static final String SRC = "" +
            "pragma solidity ^0.4.8;" +
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
        contractsService = new ContractsServiceImpl();
        contractsService.ethereum = mock(Ethereum.class);

        contractsService.contractsStorage = new HashMapDB<>();
        contractsService.settingsStorage = new HashMapDB<>();
        contractsService.contractCreation = new HashMapDB<>();
        repository = mock(Repository.class);

        when(repository.getCode(any())).thenReturn(Hex.decode(CODE));
        when(contractsService.ethereum.getRepository()).thenReturn(repository);
    }

    @Test
    public void contracts_readSolcVersion() {
        assertThat(SolcUtils.getSolcVersion(), is("0.4.19"));
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
                new MockMultipartFile("std.sol", "std.sol", "plain/text", contract1),
                new MockMultipartFile("contract.sol", "contract.sol", "plain/text", contract2)

        };
        ContractInfoDTO addedContract = contractsService.uploadContract(ADDRESS, files);

        assertThat(addedContract.getName(), is("Contract"));
    }

    @Test
    public void contracts_shouldExtractHash_1() throws Exception {
        final String testData = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("contracts/test-extract-hashes.asm.txt"));
        final Set<String> set = ContractsServiceImpl.extractFuncHashes(testData);

        assertThat(set, hasSize(3));
        assertTrue(set.contains("99372321"));
        assertTrue(set.contains("a6f9dae1"));
        assertTrue(set.contains("41c0e1b5"));
    }

    @Test
    public void contracts_shouldDetectContractName_whenSameHashes() throws Exception {
        final String sourceCode =
                "contract Contract1 { function test(bytes32 str) {}}" +
                        "contract Contract2 { function test(bytes32 str) {}}";
        final String contractBin = loadContractBin(sourceCode, "Contract1");
        when(repository.getCode(any())).thenReturn(Hex.decode(contractBin));


        ContractInfoDTO addedContract = contractsService.addContract(ADDRESS, sourceCode);

        assertThat(addedContract.getName(), containsString("Contract1"));
        assertThat(addedContract.getName(), containsString("Contract2"));
    }

    @Test
    public void contracts_shouldDetectContractName_whenNameReg() throws Exception {
        // prepare
        final String sourceCode = loadSourceCode("contracts/NameReg.sol");
        final String contractBin = loadContractBin(sourceCode, "NameReg");
        when(repository.getCode(any())).thenReturn(Hex.decode(contractBin));

        // test
        ContractInfoDTO addedContract = contractsService.addContract(ADDRESS, sourceCode);

        assertThat(addedContract.getName(), is("NameReg"));
    }

    @Test
    public void contracts_shouldDetectContractName_whenManyContracts() throws Exception {
        // prepare
        final String sourceCode = loadSourceCode("contracts/NameReg.sol");
        final String contractBin = loadContractBin(sourceCode, "NameReg");
        //when(repository.getCode(any())).thenReturn(Hex.decode(contractBin));
        String bin = "606060405260e060020a600035046341c0e1b581146044578063bb34534c146050578063e1fa8e84146086578063e79a198f146044578063f5c573821460a1575b6000565b34600057604e60c0565b005b34600057605d60043560c3565b6040805173ffffffffffffffffffffffffffffffffffffffff9092168252519081900360200190f35b34600057604e60043560cb565b005b34600057604e60c0565b005b3460005760ae60043560c3565b60408051918252519081900360200190f35b5b565b60005b919050565b5b50565b5b565b60005b91905056";
        when(repository.getCode(any())).thenReturn(Hex.decode(bin));

        // test
        MockMultipartFile[] files = {
                new MockMultipartFile("contract.sol", "contract.sol", "plain/text", loadSourceCode("contracts/Contract.sol").getBytes()),
                new MockMultipartFile("NameReg.sol", "NameReg.sol", "plain/text", loadSourceCode("contracts/NameReg.sol").getBytes()),
                new MockMultipartFile("std.sol", "std.sol", "plain/text", loadSourceCode("contracts/std.sol").getBytes())
        };
        ContractInfoDTO addedContract = contractsService.uploadContract(ADDRESS, files);

        assertThat(addedContract.getName(), is("NameReg"));
    }

    /*-------- HELPERS --------*/

    private String loadSourceCode(String path) throws IOException {
        return IOUtils.toString(getClass().getClassLoader().getResourceAsStream(path));
    }

    private String loadContractBin(String sourceCode, String contractName) throws IOException {
        final SolidityCompiler.Result compiled = SolidityCompiler.compile(sourceCode.getBytes("UTF-8"), true, SolidityCompiler.Options.BIN);
        final CompilationResult result = CompilationResult.parse(compiled.output);

        return result.getContract(contractName).bin;
    }
}

