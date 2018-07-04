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

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionInfo;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.vm.LogInfo;

import static com.ethercamp.harmony.jsonrpc.TypeConverter.toJsonHex;

/**
 * Created by Ruben on 5/1/2016.
 */
@Value
@NonFinal
public class TransactionReceiptDTO {

    public String transactionHash;          // hash of the transaction.
    public String transactionIndex;         // integer of the transactions index position in the block.
    public String blockHash;                // hash of the block where this transaction was in.
    public String blockNumber;              // block number where this transaction was in.
    public String from;                     // 20 Bytes - address of the sender.
    public String to;                       // 20 Bytes - address of the receiver. null when its a contract creation transaction.
    public String cumulativeGasUsed;        // The total amount of gas used when this transaction was executed in the block.
    public String gasUsed;                  // The amount of gas used by this specific transaction alone.
    public String contractAddress;          // The contract address created, if the transaction was a contract creation, otherwise  null .
    public JsonRpc.LogFilterElement[] logs;         // Array of log objects, which this transaction generated.
    public String logsBloom;                       // 256 Bytes - Bloom filter for light clients to quickly retrieve related logs.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String root;  // 32 bytes of post-transaction stateroot (pre Byzantium)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String status;  //  either 1 (success) or 0 (failure) (post Byzantium)

    public TransactionReceiptDTO(Block block, TransactionInfo txInfo){
        TransactionReceipt receipt = txInfo.getReceipt();

        transactionHash = toJsonHex(receipt.getTransaction().getHash());
        transactionIndex = toJsonHex(new Integer(txInfo.getIndex()).longValue());
        cumulativeGasUsed = toJsonHex(receipt.getCumulativeGas());
        gasUsed = toJsonHex(receipt.getGasUsed());
        contractAddress = toJsonHex(receipt.getTransaction().getContractAddress());
        from = toJsonHex(receipt.getTransaction().getSender());
        to = toJsonHex(receipt.getTransaction().getReceiveAddress());
        logs = new JsonRpc.LogFilterElement[receipt.getLogInfoList().size()];
        if (block != null) {
            blockNumber = toJsonHex(block.getNumber());
            blockHash = toJsonHex(txInfo.getBlockHash());
        } else {
            blockNumber = null;
            blockHash = null;
        }

        for (int i = 0; i < logs.length; i++) {
            LogInfo logInfo = receipt.getLogInfoList().get(i);
            logs[i] = new JsonRpc.LogFilterElement(logInfo, block, txInfo.getIndex(),
                    txInfo.getReceipt().getTransaction(), i);
        }
        logsBloom = toJsonHex(receipt.getBloomFilter().getData());

        if (receipt.hasTxStatus()) { // post Byzantium
            root = null;
            status = receipt.isTxStatusOK() ? "0x1" : "0x0";
        } else { // pre Byzantium
            root = toJsonHex(receipt.getPostTxState());
            status = null;
        }
    }
}
