package com.restmvc.beer_store.dtos.category;

import java.util.UUID;

/**
 * Simple DTO for category.
 * Include only ID and description.
 * @param id category ID
 * @param description category description
 */
public record CategorySimpleDTO(
        UUID id,
        String description
) {}
