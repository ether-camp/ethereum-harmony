package com.ethercamp.harmony.util.exception;

import static java.lang.String.format;

public class BaseException extends RuntimeException {

    public BaseException() {
        super();
    }

    public BaseException(String message) {
        super(message);
    }

    public BaseException(Throwable cause, String message, Object... args) {
        super(format(message, args), cause);
    }
}
