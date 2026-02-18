package dev.baliak.beerclient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for creating a new beer.
 * Includes optional category IDs to assign beer to categorize on creation.
 * @param beerName beer name
 * @param upc UPC barcode
 * @param quantityOnHand quantity on hand
 * @param price price
 * @param categoryIds optional set of category UUIDs
 */
public record BeerCreateRequestDTO(
        @NotBlank(message = "Beer name is required")
        @Size(min = 1, max = 50, message = "Beer name must be between 1 and 50 characters")
        String beerName,

        @NotBlank(message = "UPC is required")
        @Size(min = 1, max = 50, message = "UPC must be between 1 and 50 characters")
        String upc,

        Integer quantityOnHand,

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be positive")
        BigDecimal price,

        /**
         * Optional set of category UUIDs to assign this beer to.
         * Categories must exist or validation will fail in the service layer.
         */
        Set<UUID> categoryIds
) {}

