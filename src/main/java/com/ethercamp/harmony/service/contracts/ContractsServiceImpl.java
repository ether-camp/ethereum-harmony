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

import com.ethercamp.contrdata.ContractDataService;
import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryVmHook;
import com.ethercamp.harmony.config.HarmonyProperties;
import com.ethercamp.harmony.service.BlockchainConsts;
import com.ethercamp.harmony.util.SolcUtils;
import com.ethercamp.harmony.util.TrustSSL;
import com.ethercamp.harmony.util.exception.ContractException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import fj.data.Validation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.leveldb.LevelDbDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.program.Program;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ethercamp.harmony.util.StreamUtil.streamOf;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.*;
import static com.ethercamp.harmony.util.exception.ContractException.compilationError;
import static com.ethercamp.harmony.util.exception.ContractException.validationError;
import static org.ethereum.util.ByteUtil.*;

import com.ethercamp.harmony.model.dto.ContractObjects.*;

/**
 * Viewing contract storage variables.
 * Depends on contract-data project.
 *
 * This class operates with hex address in lowercase without 0x.
 *
 * Created by Stan Reshetnyk on 17.10.16.
 */
@Slf4j(topic = "contracts")
public class ContractsServiceImpl implements ContractsService {

    private static final Pattern FUNC_HASHES_PATTERN = Pattern.compile("(PUSH4\\s+0x)([0-9a-fA-F]{2,8})(\\s+DUP2)?(\\s+EQ\\s+[PUSH1|PUSH2])");
    private static final Pattern SOLIDITY_HEADER_PATTERN = Pattern.compile("^\\s{0,}PUSH1\\s+0x60\\s+PUSH1\\s+0x40\\s+MSTORE.+");
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final byte[] SYNCED_BLOCK_KEY = "syncedBlock".getBytes(UTF_8);

    @Autowired
    StorageDictionaryVmHook storageDictionaryVmHook;

    @Autowired
    ContractDataService contractDataService;

    @Autowired
    StorageDictionaryDb dictionaryDb;

    @Autowired
    SystemProperties config;

    @Autowired
    HarmonyProperties properties;

    @Autowired
    Ethereum ethereum;

    @Autowired
    Storage storage;

    @Autowired
    private Environment env;

    @Autowired
    private Blockchain blockchain;

    @Autowired
    @Qualifier("contractSettingsStorage")
    DbSource<byte[]> settingsStorage;

    DbSource<byte[]> contractsStorage;

    DbSource<byte[]> contractCreation;

    /**
     * Contract data will be fully available from this block.
     * Usually this is pivot block in fast sync or zero block for regular sync.
     */
    volatile Optional<Long> syncedBlock = Optional.empty();   // undetected yet

    ObjectToBytesFormat<ContractEntity> contractFormat = new ObjectToBytesFormat<>(ContractEntity.class);

    @PostConstruct
    public void init() {
        contractsStorage = new LevelDbDataSource("contractsStorage");
        contractsStorage.init();

        contractCreation = new LevelDbDataSource("contractCreation");
        contractCreation.init();

        syncedBlock = Optional.ofNullable(settingsStorage.get(SYNCED_BLOCK_KEY))
                .map(ByteUtil::byteArrayToLong);

        syncedBlock.ifPresent(syncStart -> log.info("Contract service is set to track from block #{}", syncStart));

        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onBlock(Block block, List<TransactionReceipt> receipts) {

                // if first loaded block is null - let's save first imported block as starting point for contracts
                // track block from which we started sync
                if (!syncedBlock.isPresent()) {
                    syncedBlock = Optional.of(block.getNumber());
                    settingsStorage.put(SYNCED_BLOCK_KEY, longToBytesNoLeadZeroes(block.getNumber()));
                    settingsStorage.flush();
                    log.info("Synced block is set to #{}", block.getNumber());
                }

                // store block number of each new contract
                receipts.stream()
                        .flatMap(r -> streamOf(r.getTransaction().getContractAddress()))
                        .forEach(address -> {
                            log.info("Marked contract creation block {} {}", Hex.toHexString(address), block.getNumber());
                            contractCreation.put(address, longToBytesNoLeadZeroes(block.getNumber()));
                            contractCreation.flush();
                        });
            }
        });
        log.info("Initialized contracts. Synced block is #{}", syncedBlock.map(Object::toString).orElseGet(() -> "Undefined"));

        TrustSSL.apply();
    }

    @Override
    public boolean deleteContract(String address) {
        contractsStorage.delete(Hex.decode(address));
        return true;
    }

    @Override
    public ContractInfoDTO addContract(String address, String src) throws Exception {
        return compileAndSave(address, Arrays.asList(src));
    }

    @Override
    public List<ContractInfoDTO> getContracts() {
        return contractsStorage.keys().stream()
                .map(a -> {
                    final ContractEntity contract = loadContract(a);
                    final Long blockNumber = getContractBlock(a);
                    return new ContractInfoDTO(Hex.toHexString(a), contract.getName(), blockNumber);
                })
                .sorted((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()))
                .collect(toList());
    }

    private long getContractBlock(byte[] address) {
        return Optional.ofNullable(contractCreation.get(address)).map(b -> byteArrayToLong(b)).orElse(-1L);
    }

    @Override
    public ContractInfoDTO uploadContract(String address, MultipartFile[] files) throws Exception {
        return compileAndSave(address, Source.toPlain(files));
    }

    @Override
    public IndexStatusDTO getIndexStatus() throws Exception {
        final long totalSize = Arrays.asList("/storageDict", "/contractCreation").stream()
                .mapToLong(name -> FileUtils.sizeOfDirectory(new File(config.databaseDir() + name)))
                .sum();

        return new IndexStatusDTO(
                totalSize,
                SolcUtils.getSolcVersion(),
                syncedBlock.orElse(-1L));
    }

    /**
     * Get contract storage entries.
     *
     * @param hexAddress - address of contract
     * @param path - nested level of fields
     * @param pageable - for paging
     */
    @Override
    public Page<StorageEntry> getContractStorage(String hexAddress, String path, Pageable pageable) {
        final byte[] address = Hex.decode(hexAddress);
        final ContractEntity contract = Optional.ofNullable(contractsStorage.get(address))
                .map(bytes -> contractFormat.decode(bytes))
                .orElseThrow(() -> new RuntimeException("Contract sources not found"));

        final StoragePage storagePage = getContractData(hexAddress, contract.getDataMembers(), Path.parse(path), pageable.getPageNumber(), pageable.getPageSize());

        final PageImpl<StorageEntry> storage = new PageImpl<>(storagePage.getEntries(), pageable, storagePage.getTotal());

        return storage;
    }


    protected StoragePage getContractData(String address, String contractDataJson, Path path, int page, int size) {
        byte[] contractAddress = Hex.decode(address);
        StorageDictionary dictionary = getDictionary(contractAddress);

        ContractData contractData = ContractData.parse(contractDataJson, dictionary);

        final boolean hasFullIndex = contractCreation.get(contractAddress) != null;

        if (!hasFullIndex) {
            contractDataService.fillMissingKeys(contractData);
        }

        return contractDataService.getContractData(contractAddress, contractData, false, path, page, size);
    }

    protected StorageDictionary getDictionary(byte[] address) {
        return dictionaryDb.getDictionaryFor(Layout.Lang.solidity, address);
    }

    private String getValidatedAbi(String address, String contractName, CompilationResult result) {
        log.debug("getValidatedAbi address:{}, contractName: {}", address, contractName);
        final ContractMetadata metadata = result.getContracts().get(contractName);
        String realContractName = cleanContractName(contractName);
        if (metadata == null) {
            throw validationError("Contract with name '%s' not found in uploaded sources.", realContractName);
        }

        final String abi = metadata.getAbi();
        final CallTransaction.Contract contract = new CallTransaction.Contract(abi);
        if (ArrayUtils.isEmpty(contract.functions)) {
            throw validationError("Contract with name '%s' not found in uploaded sources.", realContractName);
        }

        final List<CallTransaction.FunctionType> funcTypes = asList(CallTransaction.FunctionType.function, CallTransaction.FunctionType.constructor);
        final Set<String> funcHashes = stream(contract.functions)
                .filter(function -> funcTypes.contains(function.type))
                .map(func -> {
                    log.debug("Compiled funcHash " + toHexString(func.encodeSignature()) + " " + func.name);
                    return toHexString(func.encodeSignature());
                })
                .collect(toSet());


        final String code = toHexString(ethereum.getRepository().getCode(Hex.decode(address)));
        final String asm = getAsm(code);
        if (isBlank(asm)) {
            throw validationError("Wrong account type: account with address '%s' hasn't any code.", address);
        }

        final Set<String> extractFuncHashes = extractFuncHashes(asm);
        extractFuncHashes.forEach(h -> log.debug("Extracted ASM funcHash " + h));
        extractFuncHashes.forEach(funcHash -> {
            if (!funcHashes.contains(funcHash)) {
                throw validationError("Incorrect code version: function with hash '%s' not found.", funcHash);
            }
        });
        log.debug("Contract is valid " + realContractName);
        return abi;
    }

    static Set<String> extractFuncHashes(String asm) {
        Set<String> result = new HashSet<>();

//        String beforeJumpDest = substringBefore(asm, "JUMPDEST");
        Matcher matcher = ContractsServiceImpl.FUNC_HASHES_PATTERN.matcher(asm);
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
     * Save contract if valid one is found, or merge names.
     * @return contract name(s) from matched file
     */
    private ContractInfoDTO compileAndSave(String hexAddress, List<String> files) {
        final byte[] address = Hex.decode(hexAddress);

        final byte[] codeBytes = ethereum.getRepository().getCode(address);
        if (codeBytes == null || codeBytes.length == 0) {
            throw validationError("Account with address '%s' hasn't any code. Please ensure blockchain is fully synced.", hexAddress);
        }

        // get list of contracts which match to deployed code
        final List<Validation<ContractException, ContractEntity>> validationResult = files.stream()
                .flatMap(src -> {
                    final CompilationResult result = compileAbi(src.getBytes());

                    return result.getContracts().entrySet().stream()
                            .map(entry -> validateContracts(hexAddress, src, result, entry.getKey()));

                }).collect(Collectors.toList());

        final List<ContractEntity> validContracts = validationResult.stream()
                .filter(v -> v.isSuccess())
                .map(v -> v.success())
                .collect(toList());

        if (!validContracts.isEmpty()) {
            // SUCCESS

            // join contract names if there are few with same signature
            // in that way we will provide more information for a user
            final String contractName = validContracts.stream()
                    .map(cc -> cc.getName())
                    .distinct()
                    .collect(joining("|"));

            // save
            validContracts.stream()
                    .findFirst()
                    .ifPresent(entity -> {
                        entity.name = contractName;
                        contractsStorage.put(address, contractFormat.encode(entity));
                        contractsStorage.flush();
                    });

            return new ContractInfoDTO(hexAddress, contractName, getContractBlock(address));
        } else {
            if (validationResult.size() == 1) {
                throw validationResult.stream()
                        .findFirst()
                        .map(v -> v.fail())
                        .get();
            } else {
                throw validationError("Target contract source not found within uploaded sources.");
            }
        }
    }

    private Validation<ContractException, ContractEntity> validateContracts(String address, String src,
                                                                             CompilationResult result,
                                                                             String name) {
        try {
            final String abi = getValidatedAbi(address, name, result);
            final String realContractName = cleanContractName(name);
            final String dataMembers = compileAst(src.getBytes()).getContractAllDataMembers(realContractName).toJson();

            final ContractEntity contract = new ContractEntity(realContractName, src, dataMembers, abi);

            return Validation.success(contract);
        } catch (ContractException e) {
            log.debug("Problem with contract. " + e.getMessage());
            return Validation.fail(e);
        }
    }

    /**
     * Since Solidity 0.4.9, contract name in output starts with '<stdin>:'
     * if contract text is provided via stdin.
     * This method safely removes this part of contract name
     * @param name  Contract name
     * @return  Real contract name
     */
    private String cleanContractName(final String name) {
        if (name.startsWith("<stdin>:")) {
            return name.substring(8);
        }

        return name;
    }

    private ContractEntity loadContract(byte[] address) {
        final byte[] loadedBytes = contractsStorage.get(address);
        return contractFormat.decode(loadedBytes);
    }

    private static CompilationResult parseCompilationResult(String rawJson) throws IOException {
        return new ObjectMapper().readValue(rawJson, CompilationResult.class);
    }

    private boolean equals(byte[] b1, byte[] b2) {
        return new ByteArrayWrapper(b1).equals(new ByteArrayWrapper(b2));
    }

    @Override
    public boolean importContractFromExplorer(String hexAddress) throws Exception {
        final byte[] address = Hex.decode(hexAddress);
        final String explorerHost = Optional.ofNullable(blockchain.getBlockByNumber(0l))
                .map(block -> Hex.toHexString(block.getHash()))
                .flatMap(hash -> BlockchainConsts.getNetworkInfo(env, hash).getSecond())
                .orElseThrow(() -> new RuntimeException("Can't import contract for this network"));

        final String url = String.format("%s/api/v1/accounts/%s/smart-storage/export", explorerHost, hexAddress);
        log.info("Importing contract:{} from:{}", hexAddress, url);
        final JsonNode result = Unirest.get(url).asJson().getBody();

        final JSONObject resultObject = result.getObject();
        final Map<String, String> map = new HashedMap<>();
        resultObject.keySet().stream()
                .forEach(k -> map.put((String) k, resultObject.getString((String) k)));

        contractDataService.importDictionary(address, map);

        contractCreation.put(address, longToBytesNoLeadZeroes(-2L));
        contractCreation.flush();
        return true;
    }

    /**
     * For testing purpose.
     */
    @Override
    public void clearContractStorage(String hexAddress) throws Exception {
        final byte[] address = Hex.decode(hexAddress);
        log.info("Clear storage of contract:{}", hexAddress);
        contractDataService.clearDictionary(address);
        contractCreation.delete(address);

        // re-import to fill members
        final ContractEntity contractEntity = loadContract(address);
        compileAndSave(hexAddress, Arrays.asList(contractEntity.src));
    }

    public static boolean isContractStorageCreated(final DbSource<byte[]> settingsStorage) {
        Optional<Long> syncedBlock = Optional.ofNullable(settingsStorage.get(SYNCED_BLOCK_KEY))
                .map(ByteUtil::byteArrayToLong);

        return syncedBlock.isPresent();
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
