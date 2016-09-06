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
import org.ethereum.core.TransactionInfo;

import static com.ethercamp.harmony.jsonrpc.TypeConverter.toJsonHex;

/**
 * Created by Anton Nashatyrev on 05.08.2016.
 */
public class TransactionReceiptDTOExt extends TransactionReceiptDTO {

    public String returnData;
    public String error;

    public TransactionReceiptDTOExt(Block block, TransactionInfo txInfo) {
        super(block, txInfo);
        returnData = toJsonHex(txInfo.getReceipt().getExecutionResult());
        error = txInfo.getReceipt().getError();
    }
}
