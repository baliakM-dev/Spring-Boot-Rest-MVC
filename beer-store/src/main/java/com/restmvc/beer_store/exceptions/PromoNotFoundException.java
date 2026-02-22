package com.restmvc.beer_store.exceptions;

/**
 * Thrown when a promotion code entered by the customer does not exist in the database.
 * Mapped to HTTP 404 Not Found by {@link GlobalExceptionsHandler}.
 */
public class PromoNotFoundException extends RuntimeException {

    public PromoNotFoundException(String code) {
        super("Promotion not found with code: '" + code + "'");
    }
}
