package com.restmvc.beer_store.dtos.beerCategory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight DTO representing a category item in a list view.
 *
 * <p>Used when listing categories in contexts where only basic metadata is needed.</p>
 *
 * @param id          category UUID
 * @param version     optimistic locking version
 * @param description category description
 * @param createdAt   creation timestamp (populated by JPA auditing)
 * @param updatedAt   last modification timestamp (populated by JPA auditing)
 */
public record CategoryListItemDTO(
        UUID id,
        Integer version,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
