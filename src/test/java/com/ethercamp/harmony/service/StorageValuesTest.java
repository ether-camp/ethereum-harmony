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

import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.FrontierConfig;
import org.ethereum.core.AccountState;
import org.ethereum.db.ContractDetails;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.util.blockchain.StandaloneBlockchain;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

/**
 * Created by Stan Reshetnyk on 26.10.16.
 */
public class StorageValuesTest {

    static {
        System.setProperty("logback.configurationFile", "src/test/resources/logback.xml");
    }

    private static final String contractSrc =
            "pragma solidity ^0.4.2;\n" +
            "contract Calculator {" +
            "  int public result;" +  // public field can be accessed by calling 'result' function
            "  function add(int num) {" +
            "    result = result + num;" +
            "  }" +
            "  function sub(int num) {" +
            "    result = result - num;" +
            "  }" +
            "  function mul(int num) {" +
            "    result = result * num;" +
            "  }" +
            "  function div(int num) {" +
            "    result = result / num;" +
            "  }" +
            "  function clear() {" +
            "    result = 0;" +
            "  }" +
            "}";

    @Test
    public void blockchain_shouldWork() throws Exception {
        SystemProperties.getDefault().setBlockchainConfig(new FrontierConfig(new FrontierConfig.FrontierConstants() {
            @Override
            public BigInteger getMINIMUM_DIFFICULTY() {
                return BigInteger.ONE;
            }
        }));

        final StandaloneBlockchain bc = new StandaloneBlockchain().withAutoblock(true);
        System.out.println("Creating first empty block (need some time to generate DAG)...");
        bc.createBlock();
        System.out.println("Creating a contract...");
        final SolidityContract calc = bc.submitNewContract(contractSrc);
        System.out.println("Contract address " + Hex.toHexString(calc.getAddress()));
        System.out.println("Calculating...");

        calc.callFunction("add", 100);
        assertEquals(BigInteger.valueOf(100), calc.callConstFunction("result")[0]);
        calc.callFunction("add", 200);
        assertEquals(BigInteger.valueOf(300), calc.callConstFunction("result")[0]);
        calc.callFunction("mul", 10);
        assertEquals(BigInteger.valueOf(3000), calc.callConstFunction("result")[0]);
        calc.callFunction("div", 5);
        assertEquals(BigInteger.valueOf(600), calc.callConstFunction("result")[0]);
        System.out.println("Clearing...");
//        calc.callFunction("clear");
//        assertEquals(BigInteger.valueOf(0), calc.callConstFunction("result")[0]);
        System.out.println("Done.");

//        bc.getBlockchain().getRepository().getAccountsKeys().stream()
//            .map(a -> Hex.toHexString(a))
//            .forEach(a -> System.out.println("Accounts in storage: " + a));

        final AccountState accountState = bc.getBlockchain().getRepository().getAccountState(calc.getAddress());
        final ContractDetails contractDetails = bc.getBlockchain().getRepository().getContractDetails(calc.getAddress());

        System.out.println("Done 2.");
    }
}
