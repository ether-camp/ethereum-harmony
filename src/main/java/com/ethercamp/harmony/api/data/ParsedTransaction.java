package com.ethercamp.harmony.api.data;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.ethereum.core.Transaction;

/**
 * Created by Stan Reshetnyk on 17.08.16.
 */
@Value
@AllArgsConstructor(staticName = "valueOf")
public class ParsedTransaction {


    public static ParsedTransaction valueOf(Transaction t) {
        return new ParsedTransaction();
    }
}
