package dev.baliak.beerclient.exceptions;

/**
 * Thrown when the Beer Store service is unreachable or the circuit breaker is OPEN,
 * and a meaningful fallback response cannot be constructed.
 *
 * <p>Used in service methods where returning empty/default data is not acceptable
 * (e.g., fetching a single resource by ID, creating or updating a beer).</p>
 */
public class ServiceUnavailableException extends RuntimeException {

    /**
     * @param message human-readable explanation of which operation could not be completed
     */
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
