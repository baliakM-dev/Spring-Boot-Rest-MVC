package dev.baliak.beerclient.dto;

import java.util.UUID;

/**
 * Data transfer object representing a single beer category.
 *
 * @param id          unique identifier of the category
 * @param description human-readable category description
 */
public record CategoriesDTO(
        UUID id,
        String description
) {
}
