package com.restmvc.beer_store.dtos.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for updating the quantity of an existing cart item.
 *
 * <p>Used by {@code PATCH /api/v1/customers/{customerId}/cart/items/{itemId}}.</p>
 *
 * <p>Why a separate DTO from {@link AddCartItemRequestDTO}:
 * <ul>
 *   <li>PATCH only needs {@code quantity} — the item to update is already identified by
 *       {@code itemId} in the URL path. Including {@code beerId} here would be redundant
 *       and misleading (it is not used).</li>
 *   <li>Following Interface Segregation — each DTO carries exactly the fields its
 *       endpoint needs, nothing more.</li>
 * </ul>
 * </p>
 *
 * <p>Quantity must be >= 1. To remove an item entirely, use
 * {@code DELETE /cart/items/{itemId}} instead of setting quantity to 0.</p>
 */
public record UpdateCartItemQuantityRequestDTO(

        /**
         * The new absolute quantity for this cart item. Must be between 1 and 100.
         * This REPLACES the current quantity — it is not an increment.
         * To remove the item, use the DELETE endpoint instead.
         */
        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be at least 1")
        @Max(value = 100, message = "quantity cannot exceed 100")
        Integer quantity

) {}
