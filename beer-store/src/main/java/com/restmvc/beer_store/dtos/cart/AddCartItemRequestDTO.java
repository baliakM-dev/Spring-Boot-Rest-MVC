package com.restmvc.beer_store.dtos.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Request body for adding a beer to the active cart.
 *
 * <p>Used by {@code POST /api/v1/customers/{customerId}/cart/items}.</p>
 *
 * <p>Business rules enforced here (Bean Validation layer):
 * <ul>
 *   <li>beerId must be present — we need to know which beer to add.</li>
 *   <li>quantity must be >= 1 (positive) and <= 100 — prevents absurd cart lines.</li>
 * </ul>
 * Additional rules (stock availability, beer existence) are enforced in the service layer.</p>
 *
 * <p>If the same beer is already in the cart, the service increments the existing
 * CartItem quantity by this value instead of creating a duplicate row.</p>
 */
public record AddCartItemRequestDTO(

        /**
         * UUID of the beer to add. Must reference an existing beer in the catalog.
         * Validated for existence in the service layer (throws 404 if not found).
         */
        @NotNull(message = "beerId is required")
        UUID beerId,

        /**
         * Number of units to add. Must be between 1 and 100.
         * If the beer is already in the cart, this amount is added to the existing quantity.
         */
        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be at least 1")
        @Max(value = 100, message = "quantity cannot exceed 100")
        Integer quantity

) {}
