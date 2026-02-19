package dev.baliak.beerclient.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data transfer object for beer responses.
 *
 * @param id             Unique identifier for the beer
 * @param beerName       Name of the beer
 * @param upc            Universal Product Code
 * @param quantityOnHand Quantity on hand
 * @param price          Price
 * @param categories     List of categories
 * @param createdAt      Creation date
 * @param updatedAt      Last update date
 */
public record BeerResponseDTO(
        UUID id,
        String beerName,
        String upc,
        Integer quantityOnHand,
        BigDecimal price,
        List<CategoriesDTO> categories,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
