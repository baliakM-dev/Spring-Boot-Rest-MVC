package dev.baliak.beerclient.exceptions;

import org.springframework.web.client.HttpStatusCodeException;

/**
 * Thrown when the Beer Store API responds with 404 Not Found.
 *
 * <p>Wraps the original {@link HttpStatusCodeException} as cause
 * so the full HTTP response details are preserved in the stacktrace.</p>
 */
public class BeerNotFoundException extends RuntimeException {

    /**
     * @param message human-readable detail (typically from RFC 7807 ProblemDetail)
     * @param cause   the original HTTP 404 exception from RestTemplate
     */
    public BeerNotFoundException(String message, HttpStatusCodeException cause) {
        super(message, cause);
    }
}
