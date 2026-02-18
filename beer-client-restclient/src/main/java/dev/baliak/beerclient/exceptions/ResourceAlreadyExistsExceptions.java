package dev.baliak.beerclient.exceptions;

public class ResourceAlreadyExistsExceptions extends RuntimeException{
    public ResourceAlreadyExistsExceptions(String message) {
        super(message);
    }
    public ResourceAlreadyExistsExceptions(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s with %s: %s already exists", resourceName, fieldName, fieldValue));
    }
}
