package com.restmvc.beer_store.dtos.category;

import java.time.LocalDateTime;
import java.util.UUID;

public record CategoryResponseDTO(
        UUID id,
        Integer version,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
