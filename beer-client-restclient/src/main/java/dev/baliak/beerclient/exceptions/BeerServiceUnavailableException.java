package dev.baliak.beerclient.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class BeerServiceUnavailableException extends RuntimeException {

    public BeerServiceUnavailableException(String message) {
        super(message);
    }
}