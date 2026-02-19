package dev.baliak.beerclient.dto;

import java.util.UUID;

/**
 * Data transfer object for categories.
 *
 * @param id          categories unique identifier
 * @param description categories description
 */
public record CategoriesDTO(
        UUID id,
        String description
) {
}
