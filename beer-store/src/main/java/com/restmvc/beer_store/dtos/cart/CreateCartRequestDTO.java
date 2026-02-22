package com.restmvc.beer_store.dtos.cart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCartRequestDTO(
        @NotBlank
        @Size(min = 3, max = 3)
        String currency
) {}
