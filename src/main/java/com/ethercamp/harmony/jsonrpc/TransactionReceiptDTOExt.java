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
