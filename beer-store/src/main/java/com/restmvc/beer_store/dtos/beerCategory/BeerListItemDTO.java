package com.restmvc.beer_store.dtos.beerCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight DTO representing a beer item in a list view.
 *
 * <p>Used when listing beers within a category context. Intentionally excludes
 * the nested categories collection to keep the response flat and efficient.</p>
 *
 * @param id             beer UUID
 * @param beerName       name of the beer
 * @param upc            UPC barcode
 * @param quantityOnHand current stock quantity (may be null if inventory is hidden)
 * @param price          price per unit
 * @param createdAt      creation timestamp (populated by JPA auditing)
 * @param updatedAt      last modification timestamp (populated by JPA auditing)
 */
public record BeerListItemDTO(
        UUID id,
        String beerName,
        String upc,
        Integer quantityOnHand,
        BigDecimal price,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
