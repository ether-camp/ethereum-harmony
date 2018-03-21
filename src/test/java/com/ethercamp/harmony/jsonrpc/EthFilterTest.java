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

import org.ethereum.core.Block;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.ethereum.crypto.HashUtil.EMPTY_LIST_HASH;
import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.junit.Assert.assertEquals;

public class EthFilterTest {

    EthJsonRpcImpl.NewBlockFilter filter = new EthJsonRpcImpl.NewBlockFilter();
    List<Block> index = new ArrayList<>();

    private Block createBlock(int number, byte[] parentHash) {
        return new Block(parentHash,
                EMPTY_LIST_HASH, // uncleHash
                new byte[20],
                new byte[0], // log bloom - from tx receipts
                new byte[0], // difficulty computed right after block creation
                number,
                new byte[0], // (add to config ?)
                0,  // gas used - computed after running all transactions
                number,  // block time
                new byte[32],  // extra data
                new byte[0],  // mixHash (to mine)
                new byte[0],  // nonce   (to mine)
                new byte[0],  // receiptsRoot - computed after running all transactions
                EMPTY_TRIE_HASH,    // TransactionsRoot - computed after running all transactions
                new byte[]{0}, // stateRoot - computed after running all transactions
                Collections.emptyList(),
                null);  // uncle list
    }

    @Test
    public void simpleTest() {
        byte[] parentHash = new byte[32];
        for (int i = 0; i < 1106; ++i) {
            Block block = createBlock(i, parentHash);
            index.add(block);
            parentHash = block.getHash();
            if (i == 0) test0();
            filter.newBlockReceived(block);
            switch (i) {
                case 100:
                    test100();
                    break;
                case 200:
                    test200();
                    break;
                case 1100:
                    test1100();
                    break;
                case 1105:
                    test1105();
                    break;
            }
        }
    }

    @Test
    public void rarePollTest() {
        byte[] parentHash = new byte[32];
        for (int i = 0; i < 1106; ++i) {
            Block block = createBlock(i, parentHash);
            index.add(block);
            parentHash = block.getHash();
            filter.newBlockReceived(block);
            if (i == 1100) {
                assertEquals(EthJsonRpcImpl.Filter.MAX_EVENT_COUNT, filter.getAll().length);
                assertEquals(EthJsonRpcImpl.Filter.MAX_EVENT_COUNT, filter.getAll().length);
                assertEquals(EthJsonRpcImpl.Filter.MAX_EVENT_COUNT, filter.poll().length);
                assertEquals(0, filter.poll().length);
                assertEquals(EthJsonRpcImpl.Filter.MAX_EVENT_COUNT, filter.getAll().length);
                String lastBlockHashHex = (String) filter.getAll()[1023];
                String firstBlockHashHex = (String) filter.getAll()[0];
                assertEquals(TypeConverter.toJsonHex(index.get(1100).getHash()), lastBlockHashHex);
                assertEquals(TypeConverter.toJsonHex(index.get(1100-1023).getHash()), firstBlockHashHex);
            }
            if (i == 1101) {
                assertEquals(EthJsonRpcImpl.Filter.MAX_EVENT_COUNT, filter.getAll().length);
                assertEquals(1, filter.poll().length);
            }
        }
    }

    private void test0() {
        assertEquals(0, filter.getAll().length);
        assertEquals(0, filter.poll().length);
    }

    private void test100() {
        assertEquals(101, filter.getAll().length);
        assertEquals(101, filter.poll().length);
        assertEquals(0, filter.poll().length);
        assertEquals(0, filter.poll().length);
        assertEquals(101, filter.getAll().length);
    }

    private void test200() {
        assertEquals(201, filter.getAll().length);
        assertEquals(100, filter.poll().length);
        assertEquals(0, filter.poll().length);
        assertEquals(201, filter.getAll().length);
    }

    private void test1100(){
        assertEquals(EthJsonRpcImpl.Filter.MAX_EVENT_COUNT, filter.getAll().length);
        assertEquals(EthJsonRpcImpl.Filter.MAX_EVENT_COUNT, filter.getAll().length);
        assertEquals(900, filter.poll().length); // #201 last returned #1101 pushed in, so 900
        assertEquals(0, filter.poll().length);
        assertEquals(EthJsonRpcImpl.Filter.MAX_EVENT_COUNT, filter.getAll().length);
        String lastBlockHashHex = (String) filter.getAll()[1023];
        String firstBlockHashHex = (String) filter.getAll()[0];
        assertEquals(TypeConverter.toJsonHex(index.get(1100).getHash()), lastBlockHashHex);
        assertEquals(TypeConverter.toJsonHex(index.get(1100-1023).getHash()), firstBlockHashHex);
    }

    private void test1105(){
        assertEquals(EthJsonRpcImpl.Filter.MAX_EVENT_COUNT, filter.getAll().length);
        Object[] pollBlocks = filter.poll();
        assertEquals(5, pollBlocks.length); // 5 new blocks
        assertEquals(0, filter.poll().length);
        String lastBlockHashHex = (String) pollBlocks[pollBlocks.length - 1];
        String firstBlockHashHex = (String) pollBlocks[0];
        assertEquals(TypeConverter.toJsonHex(index.get(1105).getHash()), lastBlockHashHex);
        assertEquals(TypeConverter.toJsonHex(index.get(1101).getHash()), firstBlockHashHex);
    }
}
