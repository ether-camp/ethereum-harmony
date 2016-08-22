package com.ethercamp.harmony.service;

import lombok.extern.slf4j.Slf4j;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.Ethereum;
import org.ethereum.mine.MinerListener;
import org.ethereum.solidity.compiler.CompilationResult;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.util.ByteUtil;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;

import static org.ethereum.crypto.HashUtil.sha3;

/**
 * Created by Stan Reshetnyk on 19.08.16.
 */
@Slf4j(topic = "harmony")
@Service
public class PrivateMinerService {

    @Autowired
    Environment env;

    @Autowired
    Ethereum ethereum;

    @Autowired
    public Repository repository;

    /**
     * Use that sender key to sign transactions
     */
    protected final byte[] senderPrivateKey = sha3("cow".getBytes());
    protected final ECKey key = ECKey.fromPrivate(senderPrivateKey);
    // sender address is derived from the private key
    protected final byte[] senderAddress = key.getAddress();

    @PostConstruct
    public void init() throws IOException, InterruptedException {
        String isPrivateNetworkValue = env.getProperty("isPrivateNetwork", "false");
        log.info("isPrivateNetwork " + isPrivateNetworkValue);
        if (isPrivateNetworkValue.equalsIgnoreCase("true")) {
            ethereum.getBlockMiner().addListener(new MinerListener() {
                @Override
                public void miningStarted() {
                    log.info("miningStarted");
                }

                @Override
                public void miningStopped() {
                    log.info("miningStopped");
                }

                @Override
                public void blockMiningStarted(Block block) {
                    log.info("blockMiningStarted");
                }

                @Override
                public void blockMined(Block block) {
                    log.info("blockMined");
                }

                @Override
                public void blockMiningCanceled(Block block) {
                    log.info("blockMiningCanceled");
                }
            });
            ethereum.getBlockMiner().startMining();

            String contract =
                    "contract Sample {" +
                            "  int i;" +
                            "  function inc(int n) {" +
                            "    i = i + n;" +
                            "  }" +
                            "  function get() returns (int) {" +
                            "    return i;" +
                            "  }" +
                            "}";



            log.info("Compiling contract...");
            SolidityCompiler.Result result = SolidityCompiler.compile(contract.getBytes(), true,
                    SolidityCompiler.Options.ABI, SolidityCompiler.Options.BIN);
            if (result.isFailed()) {
                throw new RuntimeException("Contract compilation failed:\n" + result.errors);
            }
            CompilationResult res = CompilationResult.parse(result.output);
            if (res.contracts.isEmpty()) {
                throw new RuntimeException("Compilation failed, no contracts returned:\n" + result.errors);
            }
            CompilationResult.ContractMetadata metadata = res.contracts.values().iterator().next();
            if (metadata.bin == null || metadata.bin.isEmpty()) {
                throw new RuntimeException("Compilation failed, no binary returned:\n" + result.errors);
            }

            log.info("Sending contract to net and waiting for inclusion");
            TransactionReceipt receipt = sendTxAndWait(new byte[0], Hex.decode(metadata.bin));
            log.info("Receipt " + receipt);
            BigInteger balance = repository.getBalance(senderAddress);


            log.info("Send from address: " + Hex.toHexString(senderAddress) + " " + balance.longValue());
        }
    }

    protected TransactionReceipt sendTxAndWait(byte[] receiveAddress, byte[] data) throws InterruptedException {
        BigInteger nonce = ethereum.getRepository().getNonce(senderAddress);
        Transaction tx = new Transaction(
                ByteUtil.bigIntegerToBytes(nonce),
                ByteUtil.longToBytesNoLeadZeroes(ethereum.getGasPrice()),
                ByteUtil.longToBytesNoLeadZeroes(4_000_000),
                receiveAddress,
                ByteUtil.longToBytesNoLeadZeroes(1),
                data);
        tx.sign(key);
        log.info("<=== Sending transaction: " + tx);
        ethereum.submitTransaction(tx);

        return null;
//        return waitForTx(tx.getHash());
    }
}
