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

package com.ethercamp.harmony.jsonrpc;

import com.ethercamp.harmony.keystore.FileSystemKeystore;
import com.ethercamp.harmony.service.BlockchainInfoService;
import com.ethercamp.harmony.service.ClientMessageService;
import com.ethercamp.harmony.service.ClientMessageServiceDummy;
import com.ethercamp.harmony.service.PrivateMinerService;
import com.ethercamp.harmony.service.WalletService;
import com.ethercamp.harmony.service.wallet.FileSystemWalletStore;
import com.typesafe.config.ConfigFactory;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.FrontierConfig;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.datasource.DbSettings;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.inmem.HashMapDB;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.EthereumFactory;
import org.ethereum.facade.EthereumImpl;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import static com.ethercamp.harmony.jsonrpc.TypeConverter.*;
import static java.math.BigInteger.valueOf;
import static java.util.Arrays.stream;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.ethereum.crypto.HashUtil.sha3;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Created by Anton Nashatyrev on 19.04.2016.
 */
public class JsonRpcTest {

    /**
     * OMG Setup!
     */
    private static class TestConfig {

        /**
         * Mock {@link Bean} tool
         * @param <T>   class to mock
         */
        public class MockitoFactoryBean<T> implements FactoryBean<T> {
            private final Class<T> clazz;

            public MockitoFactoryBean(Class<T> clazz) {
                this.clazz = clazz;
            }

            @Override public T getObject() throws Exception {
                return mock(clazz);
            }

            @Override public Class<T> getObjectType() {
                return clazz;
            }

            @Override public boolean isSingleton() {
                return true;
            }
        }


        private final String config =
                // no need for discovery in that small network
                "peer.discovery.enabled = false \n" +
                "peer.listen.port = 0 \n" +
                // need to have different nodeId's for the peers
                "peer.privateKey = 6ef8da380c27cea8fdf7448340ea99e8e2268fc2950d79ed47cbf6f85dc977ec \n" +
                // our private net ID
                "peer.networkId = 555 \n" +
                // we have no peers to sync with
                "sync.enabled = false \n" +
                // genesis with a lower initial difficulty and some predefined known funded accounts
                "genesis = genesis-light.json \n" +
                // two peers need to have separate database dirs
                "database.dir = sampleDB-1 \n" +
                // when more than 1 miner exist on the network extraData helps to identify the block creator
                "mine.extraDataHex = cccccccccccccccccccc \n" +
                "mine.fullDataSet = false \n" +
                "mine.cpuMineThreads = 2";

        /**
         * Instead of supplying properties via config file for the peer
         * we are substituting the corresponding bean which returns required
         * config for this instance.
         */
        @Bean
        public SystemProperties systemProperties() {
            SystemProperties props = new SystemProperties();
            props.overrideParams(ConfigFactory.parseString(config.replaceAll("'", "\"")));
            FrontierConfig config = new FrontierConfig(new FrontierConfig.FrontierConstants() {
                @Override
                public BigInteger getMINIMUM_DIFFICULTY() {
                    return BigInteger.ONE;
                }
            });
            SystemProperties.getDefault().setBlockchainConfig(config);
            props.setBlockchainConfig(config);
            return props;
        }

        @Bean
        public FileSystemKeystore keystoreManager() throws IOException {
            return new FileSystemKeystore() {
                Path keystorePath = Files.createTempDirectory("keystore");

                @Override
                public Path getKeyStoreLocation() {
                    return keystorePath;
                }
            };
        }

        @Bean
        public WalletService walletService() {
            return new WalletService();
        }

        @Bean
        public FactoryBean<PrivateMinerService> privateMinerService() {
            return new MockitoFactoryBean<>(PrivateMinerService.class);
        }

        @Bean
        public FactoryBean<BlockchainInfoService> blockchainInfoService() {
            return new MockitoFactoryBean<>(BlockchainInfoService.class);
        }

        @Bean
        public FileSystemWalletStore fileSystemWalletStore() {
            return new FileSystemWalletStore();
        }

        @Bean
        public JsonRpc jsonRpc() {
            return new EthJsonRpcImpl();
        }

        @Bean
        public TestRunner test() {
            return new TestRunner();
        }

        @Bean
        @Scope("prototype")
        public DbSource<byte[]> keyValueDataSource(String name, DbSettings settings) {
            System.out.println("Sample DB created name:" + name);
            return new HashMapDB<byte[]>();
        }

        @Bean
        public ClientMessageService clientMessageService() {
            return new ClientMessageServiceDummy();
        }
    }

    static class TestRunner {
        // ensure to publish it in other place in this class
        // see @Bean
        @Autowired
        JsonRpc jsonRpc;

        @Autowired
        Ethereum ethereum;

        /**
         * Tests, finally
         * @throws Exception
         */
        public void runTests() throws Exception {
            String passphrase = "123";
            ECKey cowKey = ECKey.fromPrivate(sha3("cow".getBytes()));
            String keydata = Hex.toHexString(cowKey.getPrivKeyBytes());
            String cowAddress = jsonRpc.personal_importRawKey(keydata, passphrase);
            assertEquals("0x" + Hex.toHexString(cowKey.getAddress()), cowAddress);
            jsonRpc.personal_unlockAccount(cowAddress, passphrase, "");

            eth_newPendingTransactionFilterTest(cowKey);

            final String contractAddress = eth_compileFilterTest(cowKey);

            eventFilter(cowKey, contractAddress);

            eth_signTest();
        }

        public void eth_newPendingTransactionFilterTest(ECKey key) throws Exception {
            System.out.println("Testing eth_newPendingTransactionFilter...");

            String keyAddress = "0x" + Hex.toHexString(key.getAddress());
            String bal0 = jsonRpc.eth_getBalance(keyAddress, "latest");
            System.out.println("Balance: " + bal0);
            assertTrue(TypeConverter.hexToBigInteger(bal0).compareTo(BigInteger.ZERO) > 0);

            String pendingTxFilterId = jsonRpc.eth_newPendingTransactionFilter();
            Object[] changes = jsonRpc.eth_getFilterChanges(pendingTxFilterId);
            assertEquals(0, changes.length);

            JsonRpc.CallArguments ca = new JsonRpc.CallArguments();
            ca.from = keyAddress;
            ca.to = "0x0000000000000000000000000000000000001234";
            ca.gas = "0x300000";
            ca.gasPrice = "0x10000000000";
            ca.value = "0x7777";
            ca.data = "0x";
            long sGas = TypeConverter.hexToBigInteger(jsonRpc.eth_estimateGas(ca)).longValue();

            String txHash1 = jsonRpc.eth_sendTransaction(new JsonRpc.CallArguments(keyAddress, "0x0000000000000000000000000000000000001234", "0x300000",
                    "0x10000000000", "0x7777", "0x", "0x00"));
            System.out.println("Tx hash: " + txHash1);
            assertTrue(TypeConverter.hexToBigInteger(txHash1).compareTo(BigInteger.ZERO) > 0);

            for (int i = 0; i < 50 && changes.length == 0; i++) {
                changes = jsonRpc.eth_getFilterChanges(pendingTxFilterId);
                Thread.sleep(200);
            }
            assertEquals(1, changes.length);
            changes = jsonRpc.eth_getFilterChanges(pendingTxFilterId);
            assertEquals(0, changes.length);

            JsonRpc.BlockResult blockResult = jsonRpc.eth_getBlockByNumber("pending", true);
            System.out.println(blockResult);
            assertEquals(txHash1, ((com.ethercamp.harmony.jsonrpc.TransactionResultDTO) blockResult.transactions[0]).hash);

            String hash1 = mineBlock();

            JsonRpc.BlockResult blockResult1 = jsonRpc.eth_getBlockByHash(hash1, true);
            assertEquals(hash1, blockResult1.hash);
            assertEquals(txHash1, ((com.ethercamp.harmony.jsonrpc.TransactionResultDTO) blockResult1.transactions[0]).hash);
            TransactionReceiptDTO receipt1 = jsonRpc.eth_getTransactionReceipt(txHash1);
            assertEquals(1, hexToLong(receipt1.blockNumber));
            assertTrue(hexToLong(receipt1.gasUsed) > 0);
            assertEquals(sGas, hexToLong(receipt1.gasUsed));

            String bal1 = jsonRpc.eth_getBalance(keyAddress, "latest");
            System.out.println("Balance: " + bal0);
            assertTrue(TypeConverter.hexToBigInteger(bal0).compareTo(TypeConverter.hexToBigInteger(bal1)) > 0);
            System.out.println("eth_newPendingTransactionFilter passed");
        }

        public String eth_compileFilterTest(ECKey key) throws Exception {
            System.out.println("Testing eth_compile and call contract...");
            String keyAddress = "0x" + Hex.toHexString(key.getAddress());
            final JsonRpc.CompilationResult compRes = jsonRpc.eth_compileSolidity(
                    "contract A { " +
                            "   event message(string msg);" +
                            "   uint public num; " +
                            "   function set(uint a) {" +
                            "       num = a; " +
                            "       log1(0x1111, 0x2222);" +
                            "   }" +
                            "   function getPublic() public constant returns (address) {" +
                            "        return msg.sender;" +
                            "   }" +
                            "   function fire() {" +
                            "       message(\"fire\");" +
                            "   }" +
                            "}");

            boolean compiledAllMethods = stream(compRes.info.abiDefinition)
                    .map(abi -> abi.name)
                    .collect(Collectors.toSet())
                    .containsAll(Arrays.asList("num", "set", "getPublic", "fire"));
            assertTrue(compiledAllMethods);

            assertTrue(compRes.code.length() > 10);

            JsonRpc.CallArguments callArgs = new JsonRpc.CallArguments();
            callArgs.from = keyAddress;
            callArgs.data = compRes.code;
            callArgs.gasPrice = "0x10000000000";
            callArgs.gas = "0x1000000";
            String txHash2 = jsonRpc.eth_sendTransaction(callArgs);
            long sGas = TypeConverter.hexToBigInteger(jsonRpc.eth_estimateGas(callArgs)).longValue();

            String hash2 = mineBlock();

            JsonRpc.BlockResult blockResult2 = jsonRpc.eth_getBlockByHash(hash2, true);
            assertEquals(hash2, blockResult2.hash);
            assertEquals(txHash2, ((com.ethercamp.harmony.jsonrpc.TransactionResultDTO) blockResult2.transactions[0]).hash);
            TransactionReceiptDTO receipt2 = jsonRpc.eth_getTransactionReceipt(txHash2);
            assertTrue(hexToLong(receipt2.blockNumber) > 1);
            assertTrue(hexToLong(receipt2.gasUsed) > 0);
            assertEquals(sGas, hexToLong(receipt2.gasUsed));
            assertTrue(hexToByteArray(receipt2.contractAddress).length == 20);

            JsonRpc.FilterRequest filterReq = new JsonRpc.FilterRequest();
            filterReq.topics = new Object[]{"0x2222"};
            filterReq.fromBlock = "latest";
            filterReq.toBlock = "latest";
            String filterId = jsonRpc.eth_newFilter(filterReq);

            CallTransaction.Function function = CallTransaction.Function.fromSignature("set", "uint");
            Transaction rawTx = ethereum.createTransaction(valueOf(2),
                    valueOf(50_000_000_000L),
                    valueOf(3_000_000),
                    hexToByteArray(receipt2.contractAddress),
                    valueOf(0), function.encode(0x777));
            rawTx.sign(key);

            String txHash3 = jsonRpc.eth_sendRawTransaction(TypeConverter.toJsonHex(rawTx.getEncoded()));

            JsonRpc.CallArguments callArgs2= createCall(receipt2.contractAddress, "num");

            String ret3 = jsonRpc.eth_call(callArgs2, "pending");
            String ret4 = jsonRpc.eth_call(callArgs2, "latest");

            String hash3 = mineBlock();

            JsonRpc.BlockResult blockResult3 = jsonRpc.eth_getBlockByHash(hash3, true);
            assertEquals(hash3, blockResult3.hash);
            assertEquals(txHash3, ((TransactionResultDTO) blockResult3.transactions[0]).hash);
            TransactionReceiptDTO receipt3 = jsonRpc.eth_getTransactionReceipt(txHash3);
            assertTrue(hexToLong(receipt3.blockNumber) > 2);
            assertTrue(hexToLong(receipt3.gasUsed) > 0);

            Object[] logs = jsonRpc.eth_getFilterChanges(filterId);
            assertEquals(1, logs.length);
            assertEquals("0x0000000000000000000000000000000000000000000000000000000000001111",
                    ((JsonRpc.LogFilterElement)logs[0]).data);
            assertEquals(0, jsonRpc.eth_getFilterChanges(filterId).length);

            String ret1 = jsonRpc.eth_call(callArgs2, blockResult2.number);
            String ret2 = jsonRpc.eth_call(callArgs2, "latest");

            assertEquals("0x0000000000000000000000000000000000000000000000000000000000000000", ret1);
            assertEquals("0x0000000000000000000000000000000000000000000000000000000000000777", ret2);
            assertEquals("0x0000000000000000000000000000000000000000000000000000000000000777", ret3);
            assertEquals("0x0000000000000000000000000000000000000000000000000000000000000000", ret4);

            {
                JsonRpc.CallArguments args = createCall(receipt2.contractAddress, "getPublic");
                String ret5 = jsonRpc.eth_call(args, blockResult2.number);

                // fall back account
                String fallBackAddress = Hex.toHexString(ECKey.DUMMY.getAddress());
                assertEquals("0x000000000000000000000000" + fallBackAddress, ret5);

                args.from = keyAddress;

                String result = jsonRpc.eth_call(args, blockResult2.number);
                assertEquals("0x000000000000000000000000cd2a3d9f938e13cd947ec05abc7fe734df8dd826", result);
            }

            {
                ECKey key2 = ECKey.fromPrivate(sha3("new address".getBytes()));
                String newAddress = Hex.toHexString(key2.getAddress());
                JsonRpc.CallArguments args = createCall(receipt2.contractAddress, "getPublic");
                args.from = "0x" + newAddress;
                String result = jsonRpc.eth_call(args, blockResult2.number);
                assertEquals("0x000000000000000000000000" + newAddress, result);
            }
            System.out.println("eth_compile and call contract passed");

            return receipt2.contractAddress;
        }

        public void eventFilter(ECKey key, String contractAddress) throws Exception {
            System.out.println("Testing event filter...");
            // Ensure event fired in contract is catched via JSON-RPC filter
            final JsonRpc.FilterRequest fr = new JsonRpc.FilterRequest();
            fr.address = contractAddress;
            final String hexFilterId = jsonRpc.eth_newFilter(fr);

            assertEquals(0, jsonRpc.eth_getFilterChanges(hexFilterId).length);

            CallTransaction.Function fun = CallTransaction.Function.fromSignature("fire");
            Transaction tx = ethereum.createTransaction(valueOf(3),
                    valueOf(50_000_000_000L),
                    valueOf(3_000_000),
                    hexToByteArray(contractAddress),
                    valueOf(0), fun.encode());
            tx.sign(key);

            String txHash = jsonRpc.eth_sendRawTransaction(TypeConverter.toJsonHex(tx.getEncoded()));

            final String blockHash = mineBlock();
            final TransactionReceiptDTOExt receipt = jsonRpc.ethj_getTransactionReceipt(txHash);
            assertTrue(isBlank(receipt.error));

            final JsonRpc.BlockResult block = jsonRpc.eth_getBlockByHash(blockHash, true);
            assertEquals(1, block.transactions.length);
            assertEquals(txHash, ((com.ethercamp.harmony.jsonrpc.TransactionResultDTO) block.transactions[0]).hash);

            final Object[] fLogs = jsonRpc.eth_getFilterLogs(hexFilterId);

            assertEquals(1, fLogs.length);
            System.out.println("Event filter passed...");
        }

        public void eth_signTest() throws Exception {
            System.out.println("Testing eth_sign...");
            final String privateKey = "4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
            final String pass = "123";
            final String msg = "Some data";
            final String expectedSignedData = "0xb91467e570a6466aa9e9876cbcd013baba02900b8979d43fe208a4a4f339f5fd6007e74cd82e037b800186422fc2da167c747ef045e5d18a5f5d4300f8e1a0291c";

            // Create and unlock account at first
            jsonRpc.personal_importRawKey(privateKey, pass);
            final String publicAddr = "0x" + Hex.toHexString(ECKey.fromPrivate(Hex.decode(privateKey)).getAddress());
            jsonRpc.personal_unlockAccount(publicAddr, pass, "100500");

            // Sign msg
            String res = jsonRpc.eth_sign(publicAddr, Hex.toHexString(msg.getBytes()));
            assertEquals(expectedSignedData, res);
            System.out.println("eth_sign passed");
        }

        JsonRpc.CallArguments createCall(String contractAddress, String functionName) {
            JsonRpc.CallArguments result = new JsonRpc.CallArguments();
            result.to = contractAddress;
            result.data = TypeConverter.toJsonHex(CallTransaction.Function.fromSignature(functionName).encode());
            return result;
        }

        String mineBlock() throws InterruptedException {
            String blockFilterId = jsonRpc.eth_newBlockFilter();
            jsonRpc.miner_start();
            int cnt = 0;
            String hash1;
            while (true) {
                Object[] blocks = jsonRpc.eth_getFilterChanges(blockFilterId);
                cnt += blocks.length;
                if (cnt > 0) {
                    hash1 = (String) blocks[0];
                    break;
                }
                Thread.sleep(100);
            }
            jsonRpc.miner_stop();
            Thread.sleep(100);
            Object[] blocks = jsonRpc.eth_getFilterChanges(blockFilterId);
            cnt += blocks.length;
            System.out.println(cnt + " blocks mined");
            boolean b = jsonRpc.eth_uninstallFilter(blockFilterId);
            assertTrue(b);
            return hash1;
        }
    }

    @Test
    public void complexTest() throws Exception {
        System.out.println("Starting Ethereum...");
        Ethereum ethereum = EthereumFactory.createEthereum(TestConfig.class);
        System.out.println("Ethereum started");
        TestRunner testRunner = ((EthereumImpl) ethereum).getApplicationContext().getBean(TestRunner.class);
        System.out.println("Starting test...");
        testRunner.runTests();
        System.out.println("Test complete.");
    }
}
