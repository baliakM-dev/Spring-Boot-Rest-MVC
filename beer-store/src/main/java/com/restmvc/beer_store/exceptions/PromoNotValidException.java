package com.restmvc.beer_store.exceptions;

/**
 * Thrown when a promotion code exists but cannot be applied to an order.
 * Covers all invalidity reasons: inactive, expired, not yet started, max uses reached.
 * Mapped to HTTP 422 Unprocessable Entity by {@link GlobalExceptionsHandler}.
 *
 * <p>The message always includes the specific reason so the API consumer
 * can display a meaningful error to the end user.</p>
 */
public class PromoNotValidException extends RuntimeException {

    public PromoNotValidException(String code, String reason) {
        super("Promotion '" + code + "' cannot be applied: " + reason);
    }
}
