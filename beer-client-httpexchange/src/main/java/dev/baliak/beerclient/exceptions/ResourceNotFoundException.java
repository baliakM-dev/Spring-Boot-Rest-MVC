package dev.baliak.beerclient.exceptions;

/**
 * Thrown when a requested resource cannot be found on the remote beer-store.
 *
 * <p>Raised inside Resilience4j fallback methods when the downstream beer-store
 * responds with {@code 404 Not Found} (e.g. no beer exists for the given UUID).
 * Handled by {@code GlobalExceptionHandler} which maps it to {@code 404 Not Found}.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final String fieldName;
    private final String fieldValue;

    /**
     * Creates an exception with a structured message built from resource details.
     *
     * <p>Produces a message of the form:
     * {@code "<resourceName> not found with <fieldName>: '<fieldValue>'"}</p>
     *
     * @param resourceName name of the resource type (e.g. "Beer")
     * @param fieldName    name of the lookup field (e.g. "id")
     * @param fieldValue   value used for the lookup (e.g. the UUID string)
     */
    public ResourceNotFoundException(String resourceName, String fieldName, String fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }
}