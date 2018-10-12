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

import com.ethercamp.harmony.model.dto.WalletAddressDTO;
import com.ethercamp.harmony.model.dto.WalletInfoDTO;
import com.ethercamp.harmony.keystore.Keystore;
import com.ethercamp.harmony.service.wallet.FileSystemWalletStore;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.Ethereum;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.blockchain.StandaloneBlockchain;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Arrays;

import static org.ethereum.crypto.HashUtil.sha3;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

/**
 * Created by Stan Reshetnyk on 30.08.16.
 */
public class WalletTests {

    private static final String ADDRESS_1 = "dc212a894a3575c61eadfb012c8db93923d806f5";

    private static final ECKey KEY_2 = ECKey.fromPrivate(sha3("dog".getBytes()));
    private static final String ADDRESS_2 = Hex.toHexString(KEY_2.getAddress());
    private static final BigInteger BALANCE_1 = BigInteger.TEN;

    WalletService walletService;

    @Before
    public void before() {
        walletService = new WalletService();
        walletService.clientMessageService = mock(ClientMessageServiceImpl.class);
        walletService.fileSystemWalletStore = mock(FileSystemWalletStore.class);
        walletService.repository = mock(Repository.class);
        walletService.keystore = mock(Keystore.class);
        walletService.ethereum = mock(Ethereum.class);

        when(walletService.fileSystemWalletStore.fromStore()).thenReturn(Arrays.asList());
        when(walletService.repository.getBalance(Hex.decode(ADDRESS_1))).thenReturn(BALANCE_1);
        when(walletService.keystore.hasStoredKey(any(String.class))).thenReturn(false);
        when(walletService.ethereum.getGasPrice()).thenReturn(0l);
    }

    @Test
    public void testEmptyWallet() throws Exception {
        WalletInfoDTO walletInfo = walletService.getWalletInfo();
        assertEquals(BigInteger.ZERO, walletInfo.getTotalAmount());
    }

    @Test
    public void testWalletWithSingleAddress() throws Exception {
        walletService.importAddress(ADDRESS_1, "cow");

        WalletInfoDTO walletInfo = walletService.getWalletInfo();
        assertEquals(BALANCE_1, walletInfo.getTotalAmount());
    }

    @Test
    public void testWalletWithPendingTransaction() throws Exception {
        final BigInteger TRANSFER_1 = BigInteger.ONE;
        final BigInteger BALANCE_1_1 = BALANCE_1.add(TRANSFER_1);
        final Transaction transaction = createTransaction(KEY_2, ADDRESS_1, BigInteger.ONE);
        walletService.importAddress(ADDRESS_1, "cow");

        walletService.handlePendingTransactionsReceived(Arrays.asList(transaction));

        {
            final WalletInfoDTO walletInfo = walletService.getWalletInfo();
            WalletAddressDTO walletAddress = walletInfo.getAddresses().get(0);
            assertEquals(BALANCE_1, walletInfo.getTotalAmount());
            assertEquals(BALANCE_1, walletAddress.getAmount());
            assertEquals(TRANSFER_1, walletAddress.getPendingAmount());
        }

        TransactionReceipt transactionReceipt = new TransactionReceipt();
        transactionReceipt.setTransaction(transaction);

        final StandaloneBlockchain sb = new StandaloneBlockchain();
        final Block block = sb.createBlock();

        BlockSummary blockSummary = new BlockSummary(block, null, Arrays.asList(transactionReceipt), null);

        {
            when(walletService.repository.getBalance(Hex.decode(ADDRESS_1))).thenReturn(BALANCE_1_1);
            walletService.handleBlock(blockSummary);

            final WalletInfoDTO walletInfo = walletService.getWalletInfo();
            WalletAddressDTO walletAddress = walletInfo.getAddresses().get(0);
            assertEquals(BALANCE_1_1, walletInfo.getTotalAmount());
            assertEquals(BALANCE_1_1, walletAddress.getAmount());
            assertEquals(BigInteger.ZERO, walletAddress.getPendingAmount());
        }
    }

    private Transaction createTransaction(ECKey fromAccount, String toAddress, BigInteger amount) {
        Transaction tx = new Transaction(
                ByteUtil.bigIntegerToBytes(BigInteger.ZERO),
                ByteUtil.longToBytesNoLeadZeroes(1_000),
                ByteUtil.longToBytesNoLeadZeroes(4_000_000),
                Hex.decode(toAddress),
                ByteUtil.bigIntegerToBytes(amount),
                ByteUtil.longToBytesNoLeadZeroes(0));
        tx.sign(fromAccount);
        return tx;
    }
}
