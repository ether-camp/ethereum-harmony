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
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryVmHook;
import com.ethercamp.harmony.service.contracts.Source;
import com.ethercamp.harmony.util.exception.ContractException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.CallTransaction;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.leveldb.LevelDbDataSource;
import org.ethereum.facade.Ethereum;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.solcJ.SolcVersion;
import org.ethereum.vm.program.Program;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.*;
import static com.ethercamp.harmony.util.exception.ContractException.compilationError;
import static com.ethercamp.harmony.util.exception.ContractException.validationError;
import static org.ethereum.util.ByteUtil.toHexString;
import com.ethercamp.harmony.dto.ContractObjects.*;

/**
 * Viewing contract storage variables.
 * Depends on contract-data project.
 *
 * This class operates with hex address in lowercase without 0x.
 *
 * Created by Stan Reshetnyk on 17.10.16.
 */
@Slf4j(topic = "contracts")
@Service
public class ContractsService {

    private static final Pattern FUNC_HASHES_PATTERN = Pattern.compile("(PUSH4\\s+0x)([0-9a-fA-F]{2,8})(\\s+DUP2)?(\\s+EQ\\s+PUSH2)");
    private static final Pattern SOLIDITY_HEADER_PATTERN = Pattern.compile("^\\s{0,}PUSH1\\s+0x60\\s+PUSH1\\s+0x40\\s+MSTORE.+");
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Autowired
    StorageDictionaryVmHook storageDictionaryVmHook;

    @Autowired
    ContractDataService contractDataService;

    @Autowired
    SystemProperties config;

    @Autowired
    Ethereum ethereum;

    DbSource<byte[]> contractsStorage;

    ObjectToBytesFormat<ContractEntity> contractFormat = new ObjectToBytesFormat<>(ContractEntity.class);

    @PostConstruct
    public void init() {
        contractsStorage = new LevelDbDataSource("contractsStorage");
        contractsStorage.init();
    }

    public boolean deleteContract(String address) {
        contractsStorage.delete(Hex.decode(address));
        return true;
    }

    public ContractInfoDTO addContract(String address, String src) {
        return compileAndSave(address, Arrays.asList(src));
    }

    public List<ContractInfoDTO> getContracts() {
        return contractsStorage.keys().stream()
                .map(a -> {
                    final ContractEntity contract = loadContract(a);
                    return new ContractInfoDTO(Hex.toHexString(a), contract.getName());
                })
                .sorted((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()))
                .collect(toList());
    }

    public ContractInfoDTO uploadContract(String address, MultipartFile[] files) {
        return compileAndSave(address, Source.toPlain(files));
    }

    public IndexStatusDTO getIndexStatus() throws IOException {
        return new IndexStatusDTO(
                FileUtils.sizeOfDirectory(new File(config.databaseDir() + "/storageDict")), SolcVersion.VERSION);
    }

    /**
     * Get contract storage entries.
     *
     * @param address - address of contract
     * @param path - nested level of fields
     * @param pageable - for paging
     */
    public Page<StorageEntry> getContractStorage(String address, String path, Pageable pageable) {
        final ContractEntity contract = Optional.ofNullable(contractsStorage.get(Hex.decode(address)))
                .map(bytes -> contractFormat.decode(bytes))
                .orElseThrow(() -> new RuntimeException("Contract sources not found"));

        final StoragePage storagePage = contractDataService.getContractData(address, contract.getDataMembers(), Path.parse(path), pageable.getPageNumber(), pageable.getPageSize());

        return new PageImpl<>(storagePage.getEntries(), pageable, storagePage.getTotal());
    }

    private String getValidatedAbi(String address, String contractName, CompilationResult result) {
        log.debug("getValidatedAbi address:{}, contractName: {}", address, contractName);
        final ContractMetadata metadata = result.getContracts().get(contractName);
        if (metadata == null) {
            throw validationError("contract with name '%s' not found in uploaded sources.", contractName);
        }

        final String abi = metadata.getAbi();
        final CallTransaction.Contract contract = new CallTransaction.Contract(abi);
        if (ArrayUtils.isEmpty(contract.functions)) {
            throw validationError("contract with name '%s' not found in uploaded sources.", contractName);
        }

        final List<CallTransaction.FunctionType> funcTypes = asList(CallTransaction.FunctionType.function, CallTransaction.FunctionType.constructor);
        final Set<String> funcHashes = stream(contract.functions)
                .filter(function -> funcTypes.contains(function.type))
                .map(func -> {
//                    log.debug("compiled funcHash " + toHexString(func.encodeSignature()) + " " + func.name);
                    return toHexString(func.encodeSignature());
                })
                .collect(toSet());


        final String code = toHexString(ethereum.getRepository().getCode(Hex.decode(address)));
        final String asm = getAsm(code);
        if (isBlank(asm)) {
            throw validationError("wrong account type: account with address '%s' hasn't any code.", address);
        }

        final Set<String> extractFuncHashes = extractFuncHashes(asm);
//        extractFuncHashes.forEach(h -> log.debug("Extracted ASM funcHash " + h));
        extractFuncHashes.forEach(funcHash -> {
            if (!funcHashes.contains(funcHash)) {
                throw validationError("incorrect code version: function with hash '%s' not found.", funcHash);
            }
        });
        return abi;
    }

    public static Set<String> extractFuncHashes(String asm) {
        Set<String> result = new HashSet<>();

//        String beforeJumpDest = substringBefore(asm, "JUMPDEST");
        Matcher matcher = FUNC_HASHES_PATTERN.matcher(asm);
        while (matcher.find()) {
            String hash = matcher.group(2);
            result.add(leftPad(hash, 8, "0"));
        }

        return result;
    }

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

    private String getAsm(String code) {
        if (isBlank(code)) return StringUtils.EMPTY;

        try {
            return Program.stringify(Hex.decode(code));
        } catch (Program.IllegalOperationException e) {
            return e.getMessage();
        }
    }

    /**
     * Try to compile each file and check if it's interface matches to asm functions hashes
     * at the deployed contract.
     * @return contract from matched file
     */
    private ContractInfoDTO compileAndSave(String address, List<String> files) {
        // get list of contracts which match to deployed code
        final List<ContractInfoDTO> validContracts = files.stream()
                .flatMap(src -> {
                    final CompilationResult result = compileAbi(src.getBytes());

                    return result.getContracts().entrySet().stream()
                            .flatMap(entry -> {
                                try {
                                    final String name = entry.getKey();
                                    final String abi = getValidatedAbi(address, name, result);
                                    final String dataMembers = compileAst(src.getBytes()).getContractAllDataMembers(name).toJson();

                                    final ContractEntity contract = new ContractEntity(name, src, dataMembers, abi);
                                    contractsStorage.put(Hex.decode(address), contractFormat.encode(contract));

                                    return Stream.of(new ContractInfoDTO(address, name));
                                } catch (ContractException e) {
                                    log.warn("Problem with contract. " + e.getMessage());
                                    return Stream.empty();
                                }
                            });

                }).collect(Collectors.toList());

        // join contract names if there are few with same signature
        return validContracts.stream()
                .findFirst()
                .map(c -> new ContractInfoDTO(
                        c.getAddress(),
                        validContracts.stream().map(cc -> cc.getName()).collect(joining("|"))))
                .orElseThrow(() -> validationError("target contract source not found within uploaded sources."));
    }

    private ContractEntity loadContract(byte[] address) {
        final byte[] loadedBytes = contractsStorage.get(address);
        return contractFormat.decode(loadedBytes);
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
        private String dataMembers;
        private String abi;

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
                return json.getBytes(UTF_8);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        public T decode(byte[] bytes) {
            try {
                return mapper.readValue(new String(bytes, UTF_8), type);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
