package dev.baliak.beerclient.exceptions;

/**
 * Thrown when an attempt is made to create a resource that already exists.
 *
 * <p>Raised inside Resilience4j fallback methods when the downstream beer-store
 * responds with {@code 409 Conflict} (e.g. a beer with the same name already exists).
 * Handled by {@code GlobalExceptionHandler} which maps it to {@code 409 Conflict}.</p>
 */
public class ResourceAlreadyExistsExceptions extends RuntimeException {

    /**
     * Creates an exception with a pre-formatted message.
     *
     * @param message full description of the conflict
     */
    public ResourceAlreadyExistsExceptions(String message) {
        super(message);
    }

    /**
     * Creates an exception with a structured message built from resource details.
     *
     * <p>Produces a message of the form:
     * {@code "<resourceName> with <fieldName>: <fieldValue> already exists"}</p>
     *
     * @param resourceName name of the resource type (e.g. "Beer")
     * @param fieldName    name of the conflicting field (e.g. "name")
     * @param fieldValue   value that caused the conflict (e.g. "Pilsner Urquell")
     */
    public ResourceAlreadyExistsExceptions(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s with %s: %s already exists", resourceName, fieldName, fieldValue));
    }
}
