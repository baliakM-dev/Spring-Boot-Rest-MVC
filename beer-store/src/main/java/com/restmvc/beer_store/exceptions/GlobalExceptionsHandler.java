package com.restmvc.beer_store.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API endpoints.
 *
 * <p>This class provides centralized exception handling across all {@code @RestController} classes
 * using Spring's {@code @RestControllerAdvice}. It converts exceptions into RFC 7807 Problem Detail
 * responses with consistent structure and appropriate HTTP status codes.</p>
 *
 * <p>Handled exceptions include:
 * <ul>
 *   <li>Validation errors (request body and request parameters)</li>
 *   <li>Resource conflicts (duplicate resources)</li>
 *   <li>Malformed JSON input</li>
 * </ul>
 * </p>
 *
 * @author Martin Baliak
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionsHandler {

    /**
     * Property name for timestamp in ProblemDetail response.
     */
    private static final String TIMESTAMP_PROPERTY = "timestamp";

    /**
     * Property name for a validation errors map in ProblemDetail response.
     */
    private static final String MESSAGE_PROPERTY = "errors";

    /**
     * Handles exceptions when attempting to create a resource that already exists.
     *
     * <p>This handler is triggered when a {@link ResourceAlreadyExistsExceptions} is thrown,
     * typically during POST operations where a resource with the same unique identifier
     * already exists in the database.</p>
     *
     * <p>Example response:
     * <pre>
     * {
     *   "type": "about:blank",
     *   "title": "Resource already exists",
     *   "status": 409,
     *   "detail": "Beer with UPC '123456' already exists",
     *   "instance": "/api/v1/beer",
     *   "timestamp": "2026-02-07T19:01:28.645538Z"
     * }
     * </pre>
     * </p>
     *
     * @param ex      the exception containing information about the duplicate resource
     * @param request the HTTP request that caused the exception
     * @return a {@link ProblemDetail} with HTTP 409 CONFLICT status
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
     * Handles validation errors for request body objects annotated with {@code @Valid} or {@code @Validated}.
     *
     * <p>This handler is triggered when validation constraints on a {@code @RequestBody} parameter fail.
     * It extracts all field-level validation errors and returns them in a structured format.</p>
     *
     * <p>Example response:
     * <pre>
     * {
     *   "type": "about:blank",
     *   "title": "Validation failed",
     *   "status": 400,
     *   "detail": "Request body validation failed.",
     *   "instance": "/api/v1/beer",
     *   "timestamp": "2026-02-07T19:01:28.645538Z",
     *   "errors": {
     *     "beerName": "nemôže byť prázdne",
     *     "price": "nemôže byť null"
     *   }
     * }
     * </pre>
     * </p>
     *
     * @param ex      the exception containing validation error details
     * @param request the HTTP request that caused the validation failure
     * @return a {@link ProblemDetail} with HTTP 400 BAD REQUEST status and validation error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                      HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult()
                .getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail pd = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "Request body validation failed.",
                request
        );
        pd.setProperty(MESSAGE_PROPERTY, errors);
        return pd;
    }

    /**
     * Handles constraint violations on request parameters (path variables, query parameters).
     *
     * <p>This handler is triggered when validation constraints on {@code @PathVariable} or
     * {@code @RequestParam} parameters fail. The controller class must be annotated with
     * {@code @Validated} for this to work.</p>
     *
     * <p>Example response:
     * <pre>
     * {
     *   "type": "about:blank",
     *   "title": "Constraint Violation",
     *   "status": 400,
     *   "detail": "Request parameters failed validation",
     *   "instance": "/api/v1/beer/abc",
     *   "timestamp": "2026-02-07T19:01:28.645538Z",
     *   "errors": {
     *     "getBeerId.beerId": "must be a valid UUID"
     *   }
     * }
     * </pre>
     * </p>
     *
     * @param ex      the exception containing constraint violation details
     * @param request the HTTP request that caused the constraint violation
     * @return a {@link ProblemDetail} with HTTP 400 BAD REQUEST status and constraint violation details
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());

        Map<String, String> errors = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (existing, _) -> existing
                ));

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Constraint Violation",
                "Request parameters failed validation",
                request
        );
        problemDetail.setProperty(MESSAGE_PROPERTY, errors);
        return problemDetail;
    }

    /**
     * Handles exceptions when the request body cannot be read or parsed.
     *
     * <p>This handler is triggered when:
     * <ul>
     *   <li>The JSON payload is malformed or invalid</li>
     *   <li>Data types don't match the expected types (e.g., string provided for numeric field)</li>
     *   <li>Required JSON fields are missing</li>
     *   <li>The request body is empty when it shouldn't be</li>
     * </ul>
     * </p>
     *
     * <p>Example response:
     * <pre>
     * {
     *   "type": "about:blank",
     *   "title": "Invalid JSON input",
     *   "status": 400,
     *   "detail": "The provided JSON is malformed or has invalid data types.",
     *   "instance": "/api/v1/beer",
     *   "timestamp": "2026-02-07T19:01:28.645538Z"
     * }
     * </pre>
     * </p>
     *
     * @param ex      the exception containing information about the parsing error
     * @param request the HTTP request that caused the exception
     * @return a {@link ProblemDetail} with HTTP 400 BAD REQUEST status
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Http message not readable: {}", ex.getMessage());
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid JSON input",
                "The provided JSON is malformed or has invalid data types.",
                request);
    }

    /**
     * Handles exceptions when a requested resource is not found.
     *
     * <p>This handler is triggered when a {@link ResourceNotFoundException} is thrown,
     * typically during GET operations where the requested ID doesn't exist.</p>
     *
     * <p>Example response:
     * <pre>
     * {
     *   "type": "about:blank",
     *   "title": "Resource not found",
     *   "status": 404,
     *   "detail": "Beer not found with id: '123e4567-e89b-12d3-a456-426614174000'",
     *   "instance": "/api/v1/beer/123e4567-e89b-12d3-a456-426614174000",
     *   "timestamp": "2026-02-07T20:45:00.123Z"
     * }
     * </pre>
     * </p>
     *
     * @param ex      the exception containing information about the missing resource
     * @param request the HTTP request that caused the exception
     * @return a {@link ProblemDetail} with HTTP 404 NOT FOUND status
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
     * Handles data integrity violations (e.g., unique constraint violation).
     *
     * <p>This handler is triggered when a database constraint is violated, typically during INSERT operations.</p>
     *
     * <p>Example response:
     * <pre>
     * {
     *     "detail": "Duplicate entry 'Crisp iu Comet #00001' for key 'beers.uk_beers_beer_name'",
     *     "instance": "/api/v1/import/beers",
     *     "status": 409,
     *     "title": "Data integrity violation",
     *     "timestamp": "2026-02-13T19:19:07.972443Z"
     * }
     * </pre>
     * </p>
     *
     * @param ex      the exception containing information about the data integrity violation
     * @param request the HTTP request that caused the exception
     * @return a {@link ProblemDetail} with HTTP 409 CONFLICT status
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Data integrity violation: {}", ex.getMessage());

        return createProblemDetail(
                HttpStatus.CONFLICT,
                "Data integrity violation",
                ex.getMostSpecificCause().getMessage(),
                request);

    }

//    @ExceptionHandler(DirectMethod)

    /**
     * Creates a standardized ProblemDetail response with common properties.
     *
     * <p>This utility method creates a {@link ProblemDetail} object conforming to RFC 7807
     * specification for HTTP API error responses. All problem details include a timestamp
     * and the request URI that caused the error.</p>
     *
     * @param status  the HTTP status code for the error response
     * @param title   a short, human-readable summary of the problem type
     * @param detail  a detailed explanation specific to this occurrence of the problem
     * @param request the current HTTP request, used to extract the request URI
     * @return a configured {@link ProblemDetail} instance ready to be returned to the client
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