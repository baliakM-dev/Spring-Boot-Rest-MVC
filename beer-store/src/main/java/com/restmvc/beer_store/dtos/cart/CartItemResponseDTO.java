package com.restmvc.beer_store.dtos.cart;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only projection of a {@link com.restmvc.beer_store.entities.CartItem}.
 *
 * <p>Returned as part of {@link CartResponseDTO#items()} so the client can display
 * the cart contents with enough detail to render a cart line (beer name, price, quantity,
 * and the subtotal for that line).</p>
 *
 * <p>Note: {@code unitPrice} is the beer's CURRENT price at the time of the response —
 * it is NOT a price snapshot. The final price is locked in only at checkout when a
 * {@link com.restmvc.beer_store.entities.BeerOrderLine} is created.</p>
 */
public record CartItemResponseDTO(

        /** UUID of this cart item — used for PATCH / DELETE /cart/items/{itemId}. */
        UUID cartItemId,

        /** UUID of the beer in this line — allows the client to link back to the beer catalog. */
        UUID beerId,

        /** Display name of the beer (denormalized here to avoid a second request). */
        String beerName,

        /**
         * Current unit price of the beer in the cart's currency.
         * This value reflects the price at response time, not at the time the item was added.
         * The final locked-in price will be recorded on BeerOrderLine at checkout.
         */
        BigDecimal unitPrice,

        /** Number of units of this beer in the cart. Always >= 1. */
        Integer quantity,

        /**
         * Convenience field: unitPrice * quantity.
         * Computed by the mapper so the client does not have to calculate it.
         */
        BigDecimal lineTotal

) {}
