package com.restmvc.beer_store.exceptions;


import java.util.UUID;

public class ActiveCartAlreadyExistsException extends RuntimeException {

    private final UUID customerId;

    public ActiveCartAlreadyExistsException(UUID customerId) {
        super("Active cart already exists for customer: " + customerId);
        this.customerId = customerId;
    }

    public UUID getCustomerId() {
        return customerId;
    }
}