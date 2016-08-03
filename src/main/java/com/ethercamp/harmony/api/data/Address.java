package com.ethercamp.harmony.api.data;

/**
 * Created by Stan Reshetnyk on 03.08.16.
 */

public class Address {

    private byte[] value;

    public Address(byte[] value) {
        if (value == null || value.length != 32) {
            throw new IllegalArgumentException("Address value is invalid");
        }
    }

    public byte[] value() {
        return value;
    }
}
