package com.restmvc.beer_store.services;

import com.restmvc.beer_store.dtos.beerCategory.BeerListItemDTO;
import com.restmvc.beer_store.entities.Beer;
import com.restmvc.beer_store.exceptions.ResourceNotFoundException;
import com.restmvc.beer_store.mappers.BeerMapper;
import com.restmvc.beer_store.mappers.CategoryMapper;
import com.restmvc.beer_store.repositories.BeerRepository;
import com.restmvc.beer_store.repositories.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeerCategoryService {

    private final BeerRepository beerRepository;
    private final CategoryRepository categoryRepository;
    private final BeerMapper beerMapper;
    private final CategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public Page<BeerListItemDTO> getBeersByCategory(UUID categoryId, Pageable pageable) {
        log.info("Retrieving beers by category with ID: {}", categoryId);

        // 1) Check if a category exists
        checkIfCategoryExist(categoryId);

        // 2) Retrieve beers by category
        Page<Beer> beers = beerRepository.findBeersByCategoryId(categoryId, pageable);
        return beers.map(beerMapper::beerToBeerListItemDto);
    }

    /**
     * Retrieves a category by its ID, throwing a ResourceNotFoundException if not found.
     * @param categoryId The ID of the category to retrieve.
     * @return The found category.
     * @throws ResourceNotFoundException If the category with the given ID is not found.
     */
    private void checkIfCategoryExist(UUID categoryId) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId.toString()));
    }

    private void checkIfBeerExist(UUID beerId) {
        beerRepository.findById(beerId)
                .orElseThrow(() -> new ResourceNotFoundException("Beer", "id", beerId.toString()));
    }
}
