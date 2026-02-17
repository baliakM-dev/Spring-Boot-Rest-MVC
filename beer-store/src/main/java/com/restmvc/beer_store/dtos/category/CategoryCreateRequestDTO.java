package com.restmvc.beer_store.dtos.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequestDTO(

        @NotBlank
        @Size(min = 1, max = 255)
        String description
) {
}
