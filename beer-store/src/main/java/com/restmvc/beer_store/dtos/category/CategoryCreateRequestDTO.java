package com.restmvc.beer_store.dtos.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating or updating a category.
 *
 * <p>Shared for both POST (create) and PUT (update) operations.</p>
 *
 * @param description category description; must not be blank, max 255 characters
 */
public record CategoryCreateRequestDTO(
        @NotBlank
        @Size(min = 1, max = 255)
        String description
) {
}
