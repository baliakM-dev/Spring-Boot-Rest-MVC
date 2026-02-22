package com.restmvc.beer_store.exceptions;

import java.util.UUID;

public class InsufficientQuantityException extends RuntimeException {

    public InsufficientQuantityException(UUID beerId, int requested, int available) {
        super("Beer " + beerId + " has insufficient stock: requested " + requested + ", available " + available);
    }
}
