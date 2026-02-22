package com.restmvc.beer_store.dtos.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Read-only projection of a {@link com.restmvc.beer_store.entities.Cart} entity.
 *
 * <p>Returned by all cart endpoints so the client always receives the full, up-to-date
 * cart state after each operation (create, add item, update quantity, remove item, etc.).</p>
 *
 * <p>{@code cartTotal} is a convenience field computed as the sum of all
 * {@link CartItemResponseDTO#lineTotal()} values. The final order total (with discount,
 * shipping, tax) is calculated separately at checkout time.</p>
 */
public record CartResponseDTO(

        /** UUID of the cart — can be used as a stable reference if needed by the client. */
        UUID cartId,

        /** UUID of the customer who owns this cart. */
        UUID customerId,

        /**
         * Current status of the cart: ACTIVE, CHECKED_OUT, or ABANDONED.
         * Stored as a String so the client is not coupled to the server-side enum.
         */
        String status,

        /** ISO 4217 currency code (e.g. "EUR", "USD") for all prices in this cart. */
        String currency,

        /**
         * All items currently in the cart, ordered by insertion time (newest last).
         * Empty list when the cart has just been created.
         */
        List<CartItemResponseDTO> items,

        /**
         * Sum of all line totals (unitPrice * quantity) across all items.
         * Computed server-side for convenience — the client does not need to sum lines.
         * Does NOT include discount, shipping, or tax — those are applied at checkout.
         */
        BigDecimal cartTotal

) {}
