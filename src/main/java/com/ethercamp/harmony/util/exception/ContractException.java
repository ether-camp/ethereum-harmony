package com.ethercamp.harmony.util.exception;

public class ContractException extends BaseException {

    public ContractException(String message) {
        super(message);
    }

    public static ContractException permissionError(String msg, Object... args) {
        return error("Contract permission error", msg, args);
    }

    public static ContractException compilationError(String msg, Object... args) {
        return error("Contract compilation error", msg, args);
    }

    public static ContractException validationError(String msg, Object... args) {
        return error("Contract validation error", msg, args);
    }

    public static ContractException assembleError(String msg, Object... args) {
        return error("Contract assemble error", msg, args);
    }

    private static ContractException error(String title, String message, Object... args) {
        return new ContractException(title + ": " + String.format(message, args));
    }
}
