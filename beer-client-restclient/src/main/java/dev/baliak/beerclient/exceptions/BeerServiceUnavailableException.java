package dev.baliak.beerclient.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when the downstream beer-store service is unavailable.
 *
 * <p>Raised by fallback methods in {@link dev.baliak.beerclient.service.BeerRestClientService}
 * when the circuit breaker is open or an infrastructure-level failure occurs.</p>
 *
 * <p>Mapped to HTTP 503 Service Unavailable by {@link GlobalExceptionHandler}.</p>
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class BeerServiceUnavailableException extends RuntimeException {

    /**
     * @param message description of why the service is unavailable
     */
    public BeerServiceUnavailableException(String message) {
        super(message);
    }
}