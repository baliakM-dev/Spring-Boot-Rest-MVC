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

/**
 * Service layer responsible for querying Beer-Category relationship data.
 *
 * <p>Provides read-only operations for retrieving beers that belong to a specific category.
 * It validates the existence of referenced resources before executing queries.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BeerCategoryService {

    private final BeerRepository beerRepository;
    private final CategoryRepository categoryRepository;
    private final BeerMapper beerMapper;
    private final CategoryMapper categoryMapper;

    /**
     * Retrieve a paginated list of beers that belong to the specified category.
     *
     * <p>First verifies that the category exists (throws 404 if not), then
     * queries beers associated with it.</p>
     *
     * @param categoryId the UUID of the category whose beers should be retrieved
     * @param pageable   pagination and sorting parameters
     * @return a page of {@link BeerListItemDTO} for beers in the given category
     * @throws ResourceNotFoundException if no category with the given ID exists
     */
    @Transactional(readOnly = true)
    public Page<BeerListItemDTO> getBeersByCategory(UUID categoryId, Pageable pageable) {
        log.info("Retrieving beers by category with ID: {}", categoryId);

        // 1) Validate that the category exists before querying beers
        checkIfCategoryExist(categoryId);

        // 2) Retrieve beers associated with the given category
        Page<Beer> beers = beerRepository.findBeersByCategoryId(categoryId, pageable);
        return beers.map(beerMapper::beerToBeerListItemDto);
    }

    /**
     * Add a category to a beer.
     *
     * @param beerId     the UUID of the beer to which the category should be added
     * @param categoryId the UUID of the category to add to the beer
     * @throws ResourceNotFoundException if either the beer or category does not exist
     */
    @Transactional
    public void addCategoryToBeer(UUID beerId, UUID categoryId) {
        log.info("Adding category with ID {} to beer with ID {}", categoryId, beerId);
        checkIfBeerExist(beerId);
        checkIfCategoryExist(categoryId);

        Beer beer = beerRepository.findById(beerId).get();
        beer.addCategory(categoryRepository.findById(categoryId).get());
    }

    /**
     * Remove a category from a beer.
     *
     * @param beerId     the UUID of the beer from which the category should be removed
     * @param categoryId the UUID of the category to remove from the beer
     */
    @Transactional
    public void deleteCategoryFromBeer(UUID beerId, UUID categoryId) {
        log.info("Removing category with ID {} from beer with ID {}", categoryId, beerId);
        checkIfBeerExist(beerId);
        checkIfCategoryExist(categoryId);
        Beer beer = beerRepository.findById(beerId).get();
        beer.removeCategory(categoryRepository.findById(categoryId).get());
    }

    // ============================= Helper Methods =================================

    /**
     * Checks that a category with the given ID exists; throws {@link ResourceNotFoundException} if not.
     *
     * @param categoryId the UUID of the category to verify
     * @throws ResourceNotFoundException if no category with the given ID exists
     */
    private void checkIfCategoryExist(UUID categoryId) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId.toString()));
    }

    /**
     * Checks that a beer with the given ID exists; throws {@link ResourceNotFoundException} if not.
     *
     * @param beerId the UUID of the beer to verify
     * @throws ResourceNotFoundException if no beer with the given ID exists
     */
    private void checkIfBeerExist(UUID beerId) {
        beerRepository.findById(beerId)
                .orElseThrow(() -> new ResourceNotFoundException("Beer", "id", beerId.toString()));
    }
}
