package com.ethercamp.harmony.model.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;

/**
 * Represents any action status
 */
@Data
public class ActionStatus<T> {

    public static <T> ActionStatus<T> createSuccessStatus(String redirectUrl) {
        ActionStatus<T> status = new ActionStatus<>();
        status.setSuccess(true);
        status.setRedirectUrl(redirectUrl);
        return status;
    }

    public static <T> ActionStatus<T> createSuccessStatus() {
        return createSuccessStatus(null);
    }

    public static <T> ActionStatus<T> createSuccessStatus(T result) {
        ActionStatus<T> status = createSuccessStatus();
        status.setResult(result);
        return status;
    }

    public static <T> ActionStatus<T> createErrorStatus(String message, Object... args) {
        ActionStatus<T> status = new ActionStatus<>();
        status.setSuccess(false);
        status.setErrorMessage(format(message, args));

        return status;
    }


    private boolean success;
    private String errorMessage;
    private Map<String, String> fieldErrors;
    private T result;
    private String redirectUrl;

    public void addFieldError(String fieldName, String error, Object... args) {
        if (fieldErrors == null) {
            fieldErrors = new HashMap<>();
        }
        fieldErrors.put(fieldName, format(error, args));
    }
}
