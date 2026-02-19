package com.restmvc.beer_store.exceptions;

/**
 * Exception thrown when attempting to create a resource that already exists.
 *
 * <p>Typically raised during POST operations when a unique constraint would be violated,
 * for example when a beer or category with the same name already exists in the database.
 * Handled by {@link GlobalExceptionsHandler} and mapped to HTTP 409 Conflict.</p>
 */
public class ResourceAlreadyExistsExceptions extends RuntimeException {

    /**
     * Creates the exception with a custom message.
     *
     * @param message the detail message
     */
    public ResourceAlreadyExistsExceptions(String message) {
        super(message);
    }

    /**
     * Creates the exception with a structured message in the format:
     * {@code "<resourceName> with <fieldName>: <fieldValue> already exists"}.
     *
     * @param resourceName the name of the resource type (e.g. "Beer", "Category")
     * @param fieldName    the name of the conflicting field (e.g. "beerName")
     * @param fieldValue   the conflicting field value
     */
    public ResourceAlreadyExistsExceptions(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s with %s: %s already exists", resourceName, fieldName, fieldValue));
    }
}
