package dev.baliak.beerclient.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TIMESTAMP_PROPERTY = "timestamp";
    private static final String MESSAGE_PROPERTY = "errors";

    @ExceptionHandler(ResourceAlreadyExistsExceptions.class)
    public ProblemDetail handleResourceAlreadyExists(ResourceAlreadyExistsExceptions ex, HttpServletRequest request) {
        log.warn("Resource already exists: {}", ex.getMessage());
        return createProblemDetail(
                HttpStatus.CONFLICT,
                "Resource already exists",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return createProblemDetail(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(BeerServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleServiceUnavailable(BeerServiceUnavailableException ex) {
        return Map.of(
                "status", 503,
                "error", "Service Unavailable",
                "message", ex.getMessage()
        );
    }

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
