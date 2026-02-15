package com.restmvc.beer_store.dtos.beer;

import java.math.BigDecimal;

public record BeerPatchRequestDTO(
        String beerName,
        String upc,
        Integer quantityOnHand,
        BigDecimal price
) {}