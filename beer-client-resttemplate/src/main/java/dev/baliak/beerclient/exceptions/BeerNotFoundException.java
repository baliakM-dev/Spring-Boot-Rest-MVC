package dev.baliak.beerclient.exceptions;

import org.springframework.web.client.HttpStatusCodeException;

public class BeerNotFoundException extends RuntimeException {
    public BeerNotFoundException(String message, HttpStatusCodeException ex) {
        super(message);
    }
}
