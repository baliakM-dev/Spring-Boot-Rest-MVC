package dev.baliak.beerclient.dto;

import java.math.BigDecimal;

/**
 * Data transfer object for beer patch requests.
 *
 * @param beerName beer name to update
 * @param upc universal product code to update
 * @param quantityOnHand quantity on hand to update
 * @param price price to update
 */
public record BeerPatchRequestDTO(
        String beerName,
        String upc,
        Integer quantityOnHand,
        BigDecimal price
) {}