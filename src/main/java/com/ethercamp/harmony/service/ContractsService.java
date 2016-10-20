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

import com.ethercamp.contrdata.ContractDataService;
import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryVmHook;
import com.ethercamp.harmony.dto.ContractInfoDTO;
import com.ethercamp.harmony.util.exception.ContractException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.core.CallTransaction;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.facade.Ethereum;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.vm.program.Program;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.*;
import static org.ethereum.solidity.compiler.ContractException.compilationError;
import static org.ethereum.solidity.compiler.ContractException.validationError;
import static org.ethereum.util.ByteUtil.toHexString;

/**
 * This class operates with hex address in lowercase without 0x.
 *
 * Created by Stan Reshetnyk on 17.10.16.
 */
@Slf4j(topic = "contracts")
@Service
public class ContractsService {

    private static final Pattern FUNC_HASHES_PATTERN = Pattern.compile("(PUSH4\\s+0x)([0-9a-fA-F]{2,8})(\\s+DUP2)?(\\s+EQ\\s+PUSH2)");
    private static final Pattern SOLIDITY_HEADER_PATTERN = Pattern.compile("^\\s{0,}PUSH1\\s+0x60\\s+PUSH1\\s+0x40\\s+MSTORE.+");

    @Autowired
    StorageDictionaryVmHook storageDictionaryVmHook;

    @Autowired
    ContractDataService contractDataService;

    @Autowired
    Ethereum ethereum;

    LevelDbDataSource contractsStorage = new LevelDbDataSource("contractsStorage");

    ObjectToBytesFormat<ContractEntity> contractFormat = new ObjectToBytesFormat<>(ContractEntity.class);

    @PostConstruct
    public void init() {
        contractsStorage.init();
    }

    public boolean deleteContract(String address) {
        contractsStorage.delete(Hex.decode(address));
        return true;
    }

    public boolean addContract(String address, String src) {
        final String name = getContractName(address, src);
        final CompilationResult result = compileAbi(src.getBytes(Charset.forName("UTF-8")));
        final String abi = getValidatedAbi(address, name, result);
        final String dataMembers = compileAst(src.getBytes()).getContractAllDataMembers(name).toJson();

        contractsStorage.put(Hex.decode(address), contractFormat.encode(new ContractEntity(name, src)));
        return true;
    }

    public List<ContractInfoDTO> getContracts() {

        final String address = "642cb487cd5631c3c965775c14178d85a476e164";
        final String src = "" +
                "contract Foo1 {\n" +
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

        addContract(address, src);
//        getContractStorage(address);

        return contractsStorage.keys().stream()
                .map(a -> {
                    final byte[] loadedBytes = contractsStorage.get(a);
                    final ContractEntity contract = contractFormat.decode(loadedBytes);
                    return new ContractInfoDTO(Hex.toHexString(a), contract.getName());
                })
                .collect(toList());
    }

    /**
     * Get contract storage entries.
     *
     * @param address - address of contract
     * @param path - nested level of fields
     * @param pageable - for paging
     */
    public StoragePage getContractStorage(String address, String path, Pageable pageable) {
        final ContractEntity contract = Optional.ofNullable(contractsStorage.get(Hex.decode(address)))
                .map(bytes -> contractFormat.decode(bytes))
                .orElseThrow(() -> new RuntimeException("Contract sources not found"));
        final CompilationResult result = compileAbi(contract.src.getBytes());
        final String abi = getValidatedAbi(address, contract.getName(), result);
        final String dataMembers = compileAst(contract.src.getBytes()).getContractAllDataMembers(contract.getName()).toJson();

//        final byte[] addressBytes = Hex.decode(address);
//        final StoragePage storageEntries = contractDataService.getStorageEntries(addressBytes, 0, 10);
//        log.info("storageEntries " + storageEntries.getSize());

        final StoragePage contractData = contractDataService.getContractData(address, dataMembers, Path.parse(path), pageable.getPageNumber(), pageable.getPageSize());

        log.info("contractData " + contractData.getSize());
        return contractData;
    }

    /**
     * Find target contract name from several ones in sources.
     */
    private String getContractName(String address, String src) {
        final CompilationResult result = compileAbi(src.getBytes());
        for (String name : result.getContracts().keySet()) {

            try {
                final String abi = getValidatedAbi(address, name, result);
                final String dataMembers = compileAst(src.getBytes()).getContractAllDataMembers(name).toJson();

                return name;

            } catch (ContractException e) {
                log.debug(format("contract '%s' verifying:", name), e);
            }
        }

        throw ContractException.validationError("target contract source not found within uploaded sources.");
    }

    private String getValidatedAbi(String address, String contractName, CompilationResult result) {
        final ContractMetadata metadata = result.getContracts().get(contractName);
        if (metadata == null) {
            throw validationError("contract with name '%s' not found in uploaded sources.", contractName);
        }

        final String abi = metadata.getAbi();
        final CallTransaction.Contract contract = new CallTransaction.Contract(abi);
        if (ArrayUtils.isEmpty(contract.functions)) {
            throw validationError("contract with name '%s' not found in uploaded sources.", contractName);
        }
        final Set<String> funcHashes = stream(contract.functions)
                .map(func -> toHexString(func.encodeSignature()))
                .collect(toSet());

        final String code = toHexString(ethereum.getRepository().getCode(Hex.decode(address)));
        final String asm = getAsm(code);
        if (isBlank(asm)) {
            throw validationError("wrong account type: account with address '%s' hasn't any code.", address);
        }
        extractFuncHashes(asm).forEach(funcHash -> {
            if (!funcHashes.contains(funcHash)) {
                throw validationError("incorrect code version: function with hash '%s' not found.", funcHash);
            }
        });
//
        return abi;

    }

    private static Set<String> extractFuncHashes(String asm) {
        Set<String> result = new HashSet<>();

        Matcher matcher = FUNC_HASHES_PATTERN.matcher(substringBefore(asm, "JUMPDEST"));
        while (matcher.find()) {
            String hash = matcher.group(2);
            result.add(leftPad(hash, 8, "0"));
        }

        return result;
    }

    // TODO: join two compilations to one method
    private static CompilationResult compileAbi(byte[] source) throws ContractException {
        try {
            SolidityCompiler.Result result = SolidityCompiler.compile(source, true, SolidityCompiler.Options.ABI);

            if (result.isFailed()) {
                throw compilationError(result.errors);
            }

            return parseCompilationResult(result.output);
        } catch (IOException e) {
            log.error("solc compilation error: ", e);
            throw compilationError(e.getMessage());
        }
    }

    private static Ast compileAst(byte[] source) {
        try {
            SolidityCompiler.Result result = SolidityCompiler.compile(source, false, SolidityCompiler.Options.AST);

            if (result.isFailed()) {
                throw compilationError(result.errors);
            }

            return Ast.parse(result.output);
        } catch (IOException e) {
            log.error("solc compilation error: ", e);
            throw compilationError(e.getMessage());
        }
    }

    public String getAsm(String code) {
        if (isBlank(code)) return StringUtils.EMPTY;

        try {
            return Program.stringify(Hex.decode(code));
        } catch (Program.IllegalOperationException e) {
            return e.getMessage();
        }
    }

    private static CompilationResult parseCompilationResult(String rawJson) throws IOException {
        return new ObjectMapper().readValue(rawJson, CompilationResult.class);
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CompilationResult {

        private Map<String, ContractMetadata> contracts;
        private String version;

    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContractMetadata {

        private String abi;

    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContractDTD {

        private Map<String, ContractMetadata> contracts;
        private String version;

    }

    /**
     * For storing in key-value database in json format.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContractEntity {

        private String name;
        private String src;

    }

    /**
     * Helper for encoding/decoding entity to bytes via json intermediate step.
     */
    public static class ObjectToBytesFormat<T> {

        final ObjectMapper mapper = new ObjectMapper();

        final Class<T> type;

        public ObjectToBytesFormat(Class<T> type) {
            this.type = type;
        }

        public byte[] encode(T entity) {
            try {
                final String json = mapper.writeValueAsString(entity);
                return json.getBytes(Charset.forName("UTF-8"));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        public T decode(byte[] bytes) {
            try {
                return mapper.readValue(new String(bytes, Charset.forName("UTF-8")), type);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
