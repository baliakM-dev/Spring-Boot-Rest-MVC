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
 * Centralized exception handler for all controllers.
 *
 * <p>Translates domain and validation exceptions into RFC 7807 {@link ProblemDetail}
 * responses, providing consistent error structure across the entire API.</p>
 *
 * <p>Handled exceptions:
 * <ul>
 *   <li>{@link ResourceAlreadyExistsExceptions} → 409 Conflict</li>
 *   <li>{@link ResourceNotFoundException} → 404 Not Found</li>
 *   <li>{@link BeerServiceUnavailableException} → 503 Service Unavailable</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request (with field errors)</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TIMESTAMP_PROPERTY = "timestamp";
    private static final String MESSAGE_PROPERTY = "errors";

    /**
     * Handles conflict when a resource already exists.
     *
     * @return 409 Conflict ProblemDetail
     */
    @ExceptionHandler(ResourceAlreadyExistsExceptions.class)
    public ProblemDetail handleResourceAlreadyExists(ResourceAlreadyExistsExceptions ex, HttpServletRequest request) {
        log.warn("Resource already exists: {}", ex.getMessage());
        return createProblemDetail(
                HttpStatus.CONFLICT,
                "Resource already exists",
                ex.getMessage(),
                request);
    }

    /**
     * Handles requests for non-existent resources.
     *
     * @return 404 Not Found ProblemDetail
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return createProblemDetail(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                ex.getMessage(),
                request);
    }

    /**
     * Handles downstream service unavailability.
     *
     * <p>Returns a simple map instead of ProblemDetail to keep the response
     * lightweight when the downstream service is down.</p>
     *
     * @return 503 Service Unavailable response body
     */
    @ExceptionHandler(BeerServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleServiceUnavailable(BeerServiceUnavailableException ex) {
        return Map.of(
                "status", 503,
                "error", "Service Unavailable",
                "message", ex.getMessage()
        );
    }

    /**
     * Handles {@code @Valid} request body validation failures.
     *
     * <p>Collects all field-level errors and adds them under the {@code "errors"}
     * property of the ProblemDetail response.</p>
     *
     * @return 400 Bad Request ProblemDetail with field error details
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
     * Builds a RFC 7807 {@link ProblemDetail} with standard fields.
     *
     * @param status  HTTP status
     * @param title   short human-readable summary
     * @param detail  detailed explanation
     * @param request current HTTP request (used to set the instance URI)
     * @return populated ProblemDetail
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
