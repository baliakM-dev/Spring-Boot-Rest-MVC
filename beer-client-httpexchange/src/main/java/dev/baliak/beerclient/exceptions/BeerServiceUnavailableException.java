package dev.baliak.beerclient.exceptions;

/**
 * Thrown when the remote beer-store service is unavailable.
 *
 * <p>Raised inside Resilience4j fallback methods when:
 * <ul>
 *   <li>a network-level error occurs (connection refused, I/O timeout)</li>
 *   <li>the CircuitBreaker is in OPEN state and blocks further calls</li>
 * </ul>
 * Handled by {@code GlobalExceptionHandler} which maps it to {@code 503 Service Unavailable}.</p>
 *
 * <p><b>Note:</b> This class must NOT carry {@code @ResponseStatus}. Combining that
 * annotation with a {@code @ExceptionHandler} in {@code GlobalExceptionHandler} causes
 * Spring to bypass the handler and return an empty response body.</p>
 */
public class BeerServiceUnavailableException extends RuntimeException {

    /**
     * Creates an exception with a combined message including the root cause.
     *
     * @param message   high-level description (e.g. "Beer store server is unavailable")
     * @param exMessage low-level cause from the original exception
     */
    public BeerServiceUnavailableException(String message, String exMessage) {
        super(message + ": " + exMessage);
    }

    /**
     * Creates an exception with a single message.
     *
     * @param message description of why the service is unavailable
     */
    public BeerServiceUnavailableException(String message) {
        super(message);
    }
}