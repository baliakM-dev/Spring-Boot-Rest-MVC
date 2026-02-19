package com.restmvc.beer_store.dtos.category;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for category responses.
 *
 * <p>Contains all category fields including audit timestamps and optimistic-locking version.</p>
 *
 * @param id          category UUID
 * @param version     optimistic locking version
 * @param description category description
 * @param createdAt   creation timestamp (populated by JPA auditing)
 * @param updatedAt   last modification timestamp (populated by JPA auditing)
 */
public record CategoryResponseDTO(
        UUID id,
        Integer version,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
