package com.restmvc.beer_store.dtos.beerCategory;

import java.time.LocalDateTime;
import java.util.UUID;

public record CategoryListItemDTO(
        UUID id,
        Integer version,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
