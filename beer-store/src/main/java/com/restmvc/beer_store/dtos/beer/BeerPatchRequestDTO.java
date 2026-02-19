package com.restmvc.beer_store.dtos.beer;

import java.math.BigDecimal;

/**
 * DTO for partial beer update requests (HTTP PATCH).
 *
 * <p>All fields are optional – only non-null values will be applied to the existing entity.
 * Null fields are ignored by the mapper thanks to
 * {@code NullValuePropertyMappingStrategy.IGNORE}.</p>
 *
 * @param beerName       new beer name (optional)
 * @param upc            new UPC barcode (optional)
 * @param quantityOnHand new quantity on hand (optional)
 * @param price          new price (optional, must be positive if provided)
 */
public record BeerPatchRequestDTO(
        String beerName,
        String upc,
        Integer quantityOnHand,
        BigDecimal price
) {}
