package com.restmvc.beer_store.exceptions;

import java.util.UUID;

public class EmptyCartException extends RuntimeException {
    public EmptyCartException(UUID cartId) {
        super("Cart " + cartId + " has no items");
    }
}
