package com.restmvc.beer_store.mappers;

import com.restmvc.beer_store.dtos.beerCategory.CategoryListItemDTO;
import com.restmvc.beer_store.dtos.category.CategoryCreateRequestDTO;
import com.restmvc.beer_store.dtos.category.CategoryResponseDTO;
import com.restmvc.beer_store.entities.Category;
import org.mapstruct.Mapper;

@Mapper
public interface CategoryMapper {
    CategoryResponseDTO categoryToResponseDto(Category category);

    Category dtoToCategory(CategoryCreateRequestDTO dto);
}
