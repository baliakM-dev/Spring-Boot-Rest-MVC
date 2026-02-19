package dev.baliak.beerclient.exceptions;

/**
 * Exception thrown when attempting to create a resource that already exists.
 *
 * <p>Typically mapped to HTTP 409 Conflict by {@link GlobalExceptionHandler}.</p>
 */
public class ResourceAlreadyExistsExceptions extends RuntimeException{

    /**
     * Creates the exception with a custom message.
     *
     * @param message description of the conflict
     */
    public ResourceAlreadyExistsExceptions(String message) {
        super(message);
    }

    /**
     * Creates the exception with a structured message derived from resource metadata.
     *
     * @param resourceName name of the resource type (e.g. "Beer")
     * @param fieldName    name of the conflicting field (e.g. "upc")
     * @param fieldValue   value that already exists
     */
    public ResourceAlreadyExistsExceptions(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s with %s: %s already exists", resourceName, fieldName, fieldValue));
    }
}
