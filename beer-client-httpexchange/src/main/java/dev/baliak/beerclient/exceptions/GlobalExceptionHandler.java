package dev.baliak.beerclient.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Centralised exception handler for the entire application.
 *
 * <p>All handlers return a {@link ProblemDetail} body (RFC 7807) so every error
 * response has a consistent structure regardless of which exception was thrown.</p>
 *
 * <p><b>Important — do NOT put {@code @ResponseStatus} on exception classes</b>
 * when a handler for that class exists here. Spring prioritises {@code @ResponseStatus}
 * on the exception class, bypasses this handler entirely, and returns an empty body
 * instead of the JSON {@link ProblemDetail}.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TIMESTAMP_PROPERTY = "timestamp";
    private static final String MESSAGE_PROPERTY = "errors";

    /**
     * Handles attempts to create a resource that already exists (HTTP 409).
     *
     * <p>Triggered when the downstream beer-store responds with {@code 409 Conflict},
     * e.g. a beer with the same name already exists.</p>
     *
     * @param ex      exception carrying the conflict message
     * @param request the current HTTP request (used to set the {@code instance} field)
     * @return {@link ProblemDetail} with status 409
     */
    @ExceptionHandler(ResourceAlreadyExistsExceptions.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleResourceAlreadyExists(ResourceAlreadyExistsExceptions ex, HttpServletRequest request) {
        log.warn("Resource already exists: {}", ex.getMessage());
        return createProblemDetail(
                HttpStatus.CONFLICT,
                "Resource already exists",
                ex.getMessage(),
                request);
    }

    /**
     * Handles requests for resources that do not exist (HTTP 404).
     *
     * <p>Triggered when the downstream beer-store responds with {@code 404 Not Found},
     * e.g. a beer with the requested UUID does not exist.</p>
     *
     * @param ex      exception carrying resource details
     * @param request the current HTTP request
     * @return {@link ProblemDetail} with status 404
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return createProblemDetail(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                ex.getMessage(),
                request);
    }

    /**
     * Handles downstream service unavailability (HTTP 503).
     *
     * <p>Triggered by the Resilience4j fallback when:
     * <ul>
     *   <li>the beer-store server is unreachable (I/O error, connection refused)</li>
     *   <li>the CircuitBreaker is in OPEN state and rejects further calls</li>
     * </ul>
     * The {@code cause} property contains the technical reason while {@code detail}
     * shows a user-friendly "please try again later" message.</p>
     *
     * @param ex      exception carrying the unavailability reason
     * @param request the current HTTP request
     * @return {@link ProblemDetail} with status 503 and a {@code cause} property
     */
    @ExceptionHandler(BeerServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ProblemDetail handleServiceUnavailable(BeerServiceUnavailableException ex, HttpServletRequest request) {
        log.warn("Beer service unavailable: {}", ex.getMessage());
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                "Beer service is currently unavailable. Please try again later.",
                request);
        problemDetail.setProperty("cause", ex.getMessage());
        return problemDetail;
    }

    /**
     * Handles request body validation failures (HTTP 400).
     *
     * <p>Triggered by {@code @Valid} on controller method parameters when the incoming
     * JSON does not satisfy Bean Validation constraints (e.g. blank name, negative price).
     * The {@code errors} property maps each invalid field name to its violation message.</p>
     *
     * @param ex      exception containing all field-level constraint violations
     * @param request the current HTTP request
     * @return {@link ProblemDetail} with status 400 and an {@code errors} map
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        DefaultMessageSourceResolvable::getDefaultMessage,
                        (existing, _) -> existing
                ));

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                "Request body failed validation",
                request
        );
        problemDetail.setProperty(MESSAGE_PROPERTY, errors);
        return problemDetail;
    }
    /**
     * Builds a {@link ProblemDetail} (RFC 7807) with common fields populated.
     *
     * @param status  HTTP status for the response
     * @param title   short, human-readable summary of the problem type
     * @param detail  human-readable explanation specific to this occurrence
     * @param request current HTTP request used to derive the {@code instance} URI
     * @return fully populated {@link ProblemDetail}
     */
    private ProblemDetail createProblemDetail(HttpStatus status,
                                              String title,
                                              String detail,
                                              HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return problemDetail;
    }
}
