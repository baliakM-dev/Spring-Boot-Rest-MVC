package dev.baliak.beerclient.exceptions;

import java.util.Map;

public class BeerValidationException extends RuntimeException {
    private Map<String, String> fieldErrors = Map.of();

    public BeerValidationException(String message, Throwable cause) {
        super(message, cause);
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}