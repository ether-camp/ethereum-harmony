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
import org.ethereum.core.Transaction;

/**
 * Created by Ruben on 8/1/2016.
 */
public class TransactionResultDTO {

    public String hash;
    public String nonce;
    public String blockHash;
    public String blockNumber;
    public String transactionIndex;

    public String from;
    public String to;
    public String gas;
    public String gasPrice;
    public String value;
    public String input;

    public TransactionResultDTO (Block b, int index, Transaction tx) {
        hash =  TypeConverter.toJsonHex(tx.getHash());
        nonce = TypeConverter.toJsonHex(tx.getNonce());
        blockHash = b == null ? null : TypeConverter.toJsonHex(b.getHash());
        blockNumber = b == null ? null : TypeConverter.toJsonHex(b.getNumber());
        transactionIndex = b == null ? null : TypeConverter.toJsonHex(index);
        from= TypeConverter.toJsonHex(tx.getSender());
        to = tx.getReceiveAddress() == null ? null : TypeConverter.toJsonHex(tx.getReceiveAddress());
        gas = TypeConverter.toJsonHex(tx.getGasLimit());
        gasPrice = TypeConverter.toJsonHex(tx.getGasPrice());
        value = TypeConverter.toJsonHex(tx.getValue());
        input  = tx.getData() != null ? TypeConverter.toJsonHex(tx.getData()) : null;
    }

    @Override
    public String toString() {
        return "TransactionResultDTO{" +
                "hash='" + hash + '\'' +
                ", nonce='" + nonce + '\'' +
                ", blockHash='" + blockHash + '\'' +
                ", blockNumber='" + blockNumber + '\'' +
                ", transactionIndex='" + transactionIndex + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", gas='" + gas + '\'' +
                ", gasPrice='" + gasPrice + '\'' +
                ", value='" + value + '\'' +
                ", input='" + input + '\'' +
                '}';
    }
}