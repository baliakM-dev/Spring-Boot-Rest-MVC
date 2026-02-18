package dev.baliak.beerclient.exceptions;


public class BeerAlreadyExistsException extends RuntimeException {

    // Use this when you want to propagate the original exception for stacktrace + debugging.
    public BeerAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    // Convenience overload if you don't care about the cause.
    public BeerAlreadyExistsException(String message) {
        super(message);
    }
}