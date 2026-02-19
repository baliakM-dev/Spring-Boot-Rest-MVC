package dev.baliak.beerclient.exceptions;

import java.util.Map;

/**
 * Thrown when the Beer Store API responds with 400 Bad Request,
 * indicating a client-side validation or malformed request error.
 *
 * <p>{@code fieldErrors} may hold per-field validation messages
 * if the downstream API provides them in a structured form.
 * When parsing fails or the API returns a plain error message,
 * {@code fieldErrors} remains empty.</p>
 */
public class BeerValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    /**
     * Creates a validation exception with a plain error message and cause.
     * {@code fieldErrors} will be empty.
     *
     * @param message human-readable detail (typically from RFC 7807 ProblemDetail)
     * @param cause   the original HTTP 400 exception from RestTemplate
     */
    public BeerValidationException(String message, Throwable cause) {
        super(message, cause);
        this.fieldErrors = Map.of();
    }

    /**
     * @return map of field name to validation message; empty if not available
     */
    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
