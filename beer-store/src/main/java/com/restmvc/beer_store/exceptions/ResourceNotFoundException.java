package com.restmvc.beer_store.exceptions;

/**
 * Exception thrown when a requested resource cannot be found.
 *
 * <p>Typically raised during GET, PUT, PATCH, or DELETE operations when the entity
 * with the given identifier does not exist in the database.
 * Handled by {@link GlobalExceptionsHandler} and mapped to HTTP 404 Not Found.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final String fieldName;
    private final String fieldValue;

    /**
     * Creates the exception with a structured message in the format:
     * {@code "<resourceName> not found with <fieldName>: '<fieldValue>'"}.
     *
     * @param resourceName the name of the resource type (e.g. "Beer", "Category")
     * @param fieldName    the name of the lookup field (e.g. "id")
     * @param fieldValue   the value that was not found
     */
    public ResourceNotFoundException(String resourceName, String fieldName, String fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }
}
