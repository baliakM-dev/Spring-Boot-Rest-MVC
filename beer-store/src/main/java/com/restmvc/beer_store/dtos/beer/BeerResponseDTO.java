package com.restmvc.beer_store.dtos.beer;

import com.restmvc.beer_store.dtos.category.CategorySimpleDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for beer response.
 * Include all fields from the Beer entity with categories.
 * @param id beer ID
 * @param beerName beer name
 * @param upc UPC barcode
 * @param quantityOnHand quantity on hand
 * @param price price
 * @param categories set of categories
 * @param createdAt creation date
 * @param updatedAt update date
 */
public record BeerResponseDTO(
        UUID id,
        String beerName,
        String upc,
        Integer quantityOnHand,
        BigDecimal price,
        Set<CategorySimpleDTO> categories,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
