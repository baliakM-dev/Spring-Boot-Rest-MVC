package com.restmvc.beer_store.dtos.beerOrder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lightweight summary of a {@link com.restmvc.beer_store.entities.BeerOrder}
 * used in list views (e.g. embedded in a customer detail response or order list endpoint).
 *
 * <p>Intentionally minimal — contains only the fields needed to display an order row
 * in a UI table or to identify an order for follow-up requests.
 * Full order details (lines, shipment, promotion) are exposed via a dedicated
 * order detail endpoint.</p>
 */
public record BeerOrderLineResponseDTO(
        UUID id,
        UUID beerId,
        String beerName,   // snapshot mena
        Integer quantity,
        BigDecimal unitPrice,    // price snapshot
        BigDecimal lineSubtotal  // quantity * unitPrice
) {}
