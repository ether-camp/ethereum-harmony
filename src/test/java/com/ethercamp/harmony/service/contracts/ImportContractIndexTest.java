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
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import com.ethercamp.harmony.model.dto.ContractObjects;
import com.ethercamp.harmony.service.BaseContextAwareTest;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.FrontierConfig;
import org.ethereum.core.Repository;
import org.ethereum.datasource.inmem.HashMapDB;
import org.ethereum.facade.Ethereum;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.VM;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by Stan Reshetnyk on 17.01.17.
 */
// FIXME: We don't have remote storage to compare anymore
@Ignore
public class ImportContractIndexTest extends BaseContextAwareTest {

    ContractsServiceImpl contractsService = new ContractsServiceImpl();

//    @Autowired
    Ethereum ethereum = mock(Ethereum.class);

    @Autowired
    StorageDictionaryDb dictionaryDb;

    @Autowired
    Repository repository;

    @Autowired
    ContractDataService contractDataService;

    @Before
    public void before() {
        contractsService.dictionaryDb = dictionaryDb;
        contractsService.contractsStorage = new HashMapDB<>();
        contractsService.settingsStorage = new HashMapDB<>();
        contractsService.contractCreation = new HashMapDB<>();
        contractsService.contractDataService = contractDataService;

        when(ethereum.getRepository()).thenReturn(repository);
        contractsService.ethereum = ethereum;
    }

    @Test
    public void contracts_importIndex() throws Exception {
        VM.setVmHook(null); //disable indexing

        System.out.println("");

        final SolidityContract solidityContract = blockchain.submitNewContract(UportRegistryContractSrc);
        blockchain.createBlock();
        solidityContract.callFunction("");
        final String hexAddress = Hex.toHexString(solidityContract.getAddress());
        final ContractObjects.ContractInfoDTO contract = contractsService.addContract(hexAddress, UportRegistryContractSrc);
        final Page<StorageEntry> storage = contractsService.getContractStorage(hexAddress, "", new PageRequest(0, 20));

        System.out.println("");

        final StorageDictionary dictionary = dictionaryDb.getDictionaryFor(Layout.Lang.solidity, Hex.decode(hexAddress));
        final StorageDictionary.PathElement root = dictionary.getByPath();
//        System.out.println(dictionary.dump());
//        System.out.println();

        loadEntries(root, "");

        dictionary.store();
        System.out.println(dictionary.dump());
    }

    private void loadEntries(StorageDictionary.PathElement root, String path) throws UnirestException {
        final String url = "https://temp.ether.camp/api/v1/accounts/a9be82e93628abaac5ab557a9b3b02f711c0151c/smart-storage?page=0&size=600&path=" + path;
        final JsonNode result = Unirest.get(url).asJson().getBody();

        final JSONArray entities = result.getObject().getJSONArray("content");

        for (int i = 0; i< entities.length(); i++) {
            System.out.println("Processing item");
            JSONObject entry = ((JSONObject) entities.get(i));
            final JSONObject key = entry.getJSONObject("key");
            final JSONObject value = entry.getJSONObject("value");
            final String[] paths = key.getString("path").split("\\|");

            if (value.getBoolean("container") == true) {
//                loadEntries(root, key);
            }

            final StorageDictionary.PathElement pe = new StorageDictionary.PathElement();

            pe.key = paths[paths.length - 1];
            pe.type = StorageDictionary.PathElement.Type.StorageIndex;

            System.out.println("Processing item " + i + " key:" + pe.key +  ", " + key.toString());
            System.out.println(value.toString());
            System.out.println();

            if (pe.key.length() == 64) {
                pe.storageKey = DataWord.of(Hex.decode(pe.key)).getData();
            } else {
                pe.storageKey = DataWord.of(Integer.valueOf(pe.key)).getData();
            }

            root.addChild(pe);
        }
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        // ignore https errors
        SSLContext sslcontext = SSLContexts.custom()
                .loadTrustMaterial(null, new TrustSelfSignedStrategy())
                .build();

        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext);
        CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .build();
        Unirest.setHttpClient(httpclient);

        SystemProperties.getDefault().setBlockchainConfig(new FrontierConfig(new FrontierConfig.FrontierConstants() {
            @Override
            public BigInteger getMINIMUM_DIFFICULTY() {
                return BigInteger.ONE;
            }
        }));
    }




    protected static String resourceToString(Resource resource) throws IOException {
        return new String(resourceToBytes(resource));
    }

    private static byte[] resourceToBytes(Resource resource) throws IOException {
        return Files.readAllBytes(Paths.get(resource.getURI()));
    }


    public static String UportRegistryContractSrc = "contract UportRegistry {\n" +
            "        event AttributesSet(address indexed _sender, uint _timestamp);\n" +
            "\n" +
            "        uint public version;\n" +
            "        address public previousPublishedVersion;\n" +
            "\n" +
            "        mapping(address => bytes) public ipfsAttributeLookup;\n" +
            "\n" +
            "        function UportRegistry(address _previousPublishedVersion) {\n" +
            "                version = 1;\n" +
            "                previousPublishedVersion = _previousPublishedVersion;\n" +
            "        }\n" +
            "\n" +
            "        function setAttributes(bytes ipfsHash) {\n" +
            "                ipfsAttributeLookup[msg.sender] = ipfsHash;\n" +
            "                AttributesSet(msg.sender, now);\n" +
            "        }\n" +
            "\n" +
            "        function getAttributes(address personaAddress) constant returns(bytes) {\n" +
            "                return ipfsAttributeLookup[personaAddress];\n" +
            "        }\n" +
            "}";

}
