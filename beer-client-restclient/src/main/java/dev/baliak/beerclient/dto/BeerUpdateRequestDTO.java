package dev.baliak.beerclient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Data transfer object for beer update requests.
 *
 * @param beerName beer name
 * @param upc UPC barcode
 * @param quantityOnHand quantity on hand
 * @param price price
 */
public record BeerUpdateRequestDTO(
        @NotBlank(message = "Beer name is required")
        @Size(min = 1, max = 50, message = "Beer name must be between 1 and 50 characters")
        String beerName,

        @NotBlank(message = "UPC is required")
        @Size(min = 1, max = 50, message = "UPC must be between 1 and 50 characters")
        String upc,

        Integer quantityOnHand,

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be positive")
        BigDecimal price
) {}
