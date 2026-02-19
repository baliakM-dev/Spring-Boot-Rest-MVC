package dev.baliak.beerclient.exceptions;

/**
 * Thrown when the Beer Store API responds with 409 Conflict,
 * indicating that a beer with the same identity (e.g., UPC) already exists.
 */
public class BeerAlreadyExistsException extends RuntimeException {

    /**
     * Use when you want to preserve the original exception for stacktrace and debugging.
     *
     * @param message human-readable detail (typically from RFC 7807 ProblemDetail)
     * @param cause   the original HTTP 409 exception from RestTemplate
     */
    public BeerAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Convenience overload when the original cause is not relevant.
     *
     * @param message human-readable detail
     */
    public BeerAlreadyExistsException(String message) {
        super(message);
    }
}
