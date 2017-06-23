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
import com.typesafe.config.ConfigFactory;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.FrontierConfig;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.inmem.HashMapDB;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.EthereumFactory;
import org.ethereum.facade.EthereumImpl;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
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

/**
 * Created by Anton Nashatyrev on 19.04.2016.
 */
public class JsonRpcTest {

    private static class TestConfig {

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
            SystemProperties.resetToDefault();
            SystemProperties props = SystemProperties.getDefault();
            props.overrideParams(ConfigFactory.parseString(config.replaceAll("'", "\"")));
            FrontierConfig config = new FrontierConfig(new FrontierConfig.FrontierConstants() {
                @Override
                public BigInteger getMINIMUM_DIFFICULTY() {
                    return BigInteger.ONE;
                }
            });
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
        public JsonRpc jsonRpc() {
            return new EthJsonRpcImpl();
        }

        @Bean
        public TestRunner test() {
            return new TestRunner();
        }

        @Bean
        @Scope("prototype")
        public DbSource<byte[]> keyValueDataSource(String name) {
            System.out.println("Sample DB created:" + name);
            return new HashMapDB<byte[]>();
        }
    }

    static class TestRunner {
        // ensure to publish it in other place in this class
        // see @Bean
        @Autowired
        JsonRpc jsonRpc;

        @Autowired
        Ethereum ethereum;

//        @PostConstruct
        public void runTests() throws Exception {
            String passphrase = "123";
            ECKey newKey = ECKey.fromPrivate(sha3("cow".getBytes()));
            String keydata = Hex.toHexString(newKey.getPrivKeyBytes());
            String cowAcct = jsonRpc.personal_importRawKey(keydata, passphrase);
            jsonRpc.personal_unlockAccount(cowAcct, passphrase, "");


            /*
             * Testing ECDSA signature in JSON-RPC
             * https://etherchain.org/verify/signature
             */
            String message = "Test message";
            byte[] dataHash = HashUtil.sha3(message.getBytes());
            System.out.println("data: " + Hex.toHexString(dataHash));
            String hexSignature = jsonRpc.eth_sign(cowAcct, "0x" + Hex.toHexString(dataHash));
//            hexSignature = "0x78161f22e473546259ce6be468666b810b32a68cdde7c6ce14c60744b9452db31e2b65a4a176d053c009f53cb8c6b06a1df161f75962bd3e2677734790a2e30500";
            byte[] bytesSignature = StringHexToByteArray(hexSignature);

            ECKey.ECDSASignature signature = ECKey.ECDSASignature.fromComponents(
                    Arrays.copyOfRange(bytesSignature, 0, 32),
                    Arrays.copyOfRange(bytesSignature, 32, 64),
                    (byte) (bytesSignature[64] + 27));

            boolean signatureRecovered = Arrays.equals(newKey.getPubKey(), ECKey.signatureToKeyBytes(dataHash, signature));
            assertTrue(signatureRecovered);

            String bal0 = jsonRpc.eth_getBalance(cowAcct, "latest");
            System.out.println("Balance: " + bal0);
            assertTrue(TypeConverter.StringHexToBigInteger(bal0).compareTo(BigInteger.ZERO) > 0);

            String pendingTxFilterId = jsonRpc.eth_newPendingTransactionFilter();
            Object[] changes = jsonRpc.eth_getFilterChanges(pendingTxFilterId);
            assertEquals(0, changes.length);

            JsonRpc.CallArguments ca = new JsonRpc.CallArguments();
            ca.from = cowAcct;
            ca.to = "0x0000000000000000000000000000000000001234";
            ca.gas = "0x300000";
            ca.gasPrice = "0x10000000000";
            ca.value = "0x7777";
            ca.data = "0x";
            long sGas = TypeConverter.StringHexToBigInteger(jsonRpc.eth_estimateGas(ca)).longValue();

            String txHash1 = jsonRpc.eth_sendTransactionArgs(cowAcct, "0x0000000000000000000000000000000000001234", "0x300000",
                    "0x10000000000", "0x7777", "0x", "0x00");
            System.out.println("Tx hash: " + txHash1);
            assertTrue(TypeConverter.StringHexToBigInteger(txHash1).compareTo(BigInteger.ZERO) > 0);

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
            assertEquals(1, HexToLong(receipt1.blockNumber));
            assertTrue(HexToLong(receipt1.gasUsed) > 0);
            assertEquals(sGas, HexToLong(receipt1.gasUsed));

            String bal1 = jsonRpc.eth_getBalance(cowAcct, "latest");
            System.out.println("Balance: " + bal0);
            assertTrue(TypeConverter.StringHexToBigInteger(bal0).compareTo(TypeConverter.StringHexToBigInteger(bal1)) > 0);

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
            callArgs.from = cowAcct;
            callArgs.data = compRes.code;
            callArgs.gasPrice = "0x10000000000";
            callArgs.gas = "0x1000000";
            String txHash2 = jsonRpc.eth_sendTransaction(callArgs);
            sGas = TypeConverter.StringHexToBigInteger(jsonRpc.eth_estimateGas(callArgs)).longValue();

            String hash2 = mineBlock();

            JsonRpc.BlockResult blockResult2 = jsonRpc.eth_getBlockByHash(hash2, true);
            assertEquals(hash2, blockResult2.hash);
            assertEquals(txHash2, ((com.ethercamp.harmony.jsonrpc.TransactionResultDTO) blockResult2.transactions[0]).hash);
            TransactionReceiptDTO receipt2 = jsonRpc.eth_getTransactionReceipt(txHash2);
            assertTrue(HexToLong(receipt2.blockNumber) > 1);
            assertTrue(HexToLong(receipt2.gasUsed) > 0);
            assertEquals(sGas, HexToLong(receipt2.gasUsed));
            assertTrue(StringHexToByteArray(receipt2.contractAddress).length == 20);

            JsonRpc.FilterRequest filterReq = new JsonRpc.FilterRequest();
            filterReq.topics = new Object[]{"0x2222"};
            filterReq.fromBlock = "latest";
            filterReq.toBlock = "latest";
            String filterId = jsonRpc.eth_newFilter(filterReq);

            CallTransaction.Function function = CallTransaction.Function.fromSignature("set", "uint");
            Transaction rawTx = ethereum.createTransaction(valueOf(2),
                    valueOf(50_000_000_000L),
                    valueOf(3_000_000),
                    StringHexToByteArray(receipt2.contractAddress),
                    valueOf(0), function.encode(0x777));
            rawTx.sign(ECKey.fromPrivate(sha3("cow".getBytes())));

            String txHash3 = jsonRpc.eth_sendRawTransaction(TypeConverter.toJsonHex(rawTx.getEncoded()));

            JsonRpc.CallArguments callArgs2= createCall(receipt2.contractAddress, "num");

            String ret3 = jsonRpc.eth_call(callArgs2, "pending");
            String ret4 = jsonRpc.eth_call(callArgs2, "latest");

            String hash3 = mineBlock();

            JsonRpc.BlockResult blockResult3 = jsonRpc.eth_getBlockByHash(hash3, true);
            assertEquals(hash3, blockResult3.hash);
            assertEquals(txHash3, ((TransactionResultDTO) blockResult3.transactions[0]).hash);
            TransactionReceiptDTO receipt3 = jsonRpc.eth_getTransactionReceipt(txHash3);
            assertTrue(HexToLong(receipt3.blockNumber) > 2);
            assertTrue(HexToLong(receipt3.gasUsed) > 0);

            Object[] logs = jsonRpc.eth_getFilterLogs(filterId);
            assertEquals(1, logs.length);
            assertEquals("0x0000000000000000000000000000000000000000000000000000000000001111",
                    ((JsonRpc.LogFilterElement)logs[0]).data);
            assertEquals(0, jsonRpc.eth_getFilterLogs(filterId).length);

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
                ECKey key = ECKey.fromPrivate(new byte[32]);
                String fallBackAddress = Hex.toHexString(key.getAddress());
                assertEquals("0x000000000000000000000000" + fallBackAddress, ret5);

                args.from = cowAcct;

                String result = jsonRpc.eth_call(args, blockResult2.number);
                assertEquals("0x000000000000000000000000cd2a3d9f938e13cd947ec05abc7fe734df8dd826", result);
            }

            {
                ECKey key = ECKey.fromPrivate(sha3("new address".getBytes()));
                String newAddress = Hex.toHexString(key.getAddress());
                JsonRpc.CallArguments args = createCall(receipt2.contractAddress, "getPublic");
                args.from = "0x" + newAddress;
                String result = jsonRpc.eth_call(args, blockResult2.number);
                assertEquals("0x000000000000000000000000" + newAddress, result);
            }

            {
                // Ensure event fired in contract is catched via JSON-RPC filter

                final String contractAddress = receipt2.contractAddress;
                final JsonRpc.FilterRequest fr = new JsonRpc.FilterRequest();
                fr.address = contractAddress;
                final String hexFilterId = jsonRpc.eth_newFilter(fr);

                assertEquals(0, jsonRpc.eth_getFilterChanges(hexFilterId).length);

                CallTransaction.Function fun = CallTransaction.Function.fromSignature("fire");
                Transaction tx = ethereum.createTransaction(valueOf(3),
                        valueOf(50_000_000_000L),
                        valueOf(3_000_000),
                        StringHexToByteArray(contractAddress),
                        valueOf(0), fun.encode());
                tx.sign(ECKey.fromPrivate(sha3("cow".getBytes())));

                String txHash = jsonRpc.eth_sendRawTransaction(TypeConverter.toJsonHex(tx.getEncoded()));

                final String blockHash = mineBlock();
                final TransactionReceiptDTOExt receipt = jsonRpc.ethj_getTransactionReceipt(txHash);
                assertTrue(isBlank(receipt.error));

                final JsonRpc.BlockResult block = jsonRpc.eth_getBlockByHash(blockHash, true);
                assertEquals(1, block.transactions.length);
                assertEquals(txHash, ((com.ethercamp.harmony.jsonrpc.TransactionResultDTO) block.transactions[0]).hash);

                final Object[] fLogs = jsonRpc.eth_getFilterLogs(hexFilterId);

                assertEquals(1, fLogs.length);
            }
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
