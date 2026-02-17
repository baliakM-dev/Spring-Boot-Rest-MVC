package com.restmvc.beer_store.dtos.beerCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record BeerListItemDTO(
        UUID id,
        String beerName,
        String upc,
        Integer quantityOnHand,
        BigDecimal price,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}