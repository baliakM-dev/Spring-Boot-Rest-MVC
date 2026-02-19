package com.restmvc.beer_store.controllers;

import com.restmvc.beer_store.dtos.beerCategory.BeerListItemDTO;
import com.restmvc.beer_store.services.BeerCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for querying Beer-Category relationships.
 *
 * <p>Exposes read-only endpoints to navigate the association between beers and categories.
 * Modification of beer-category relationships (add, remove, replace) is handled
 * through the {@link BeerController} endpoints at the beer level.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *     <li><b>GET</b> /api/v1/categories/{categoryId}/beers – List all beers in a given category</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public class BeerCategoryController {

    private final BeerCategoryService beerCategoryService;

    public static final String CATEGORIES_BASE_URL = "/api/v1/categories";
    public static final String CATEGORIES_BEERS = CATEGORIES_BASE_URL + "/{categoryId}" + "/beers";
    public static final String BEERS_BASE_URL = "/api/v1/beers";
    public static final String BEERS_CATEGORIES = BEERS_BASE_URL + "/{beerId}" + "/categories" + "/{categoryId}";

    /**
     * Retrieve a paginated list of beers belonging to the given category.
     * Example:
     * <ul>
     *     <li><b>GET</b> /api/v1/categories/{categoryId}/beers</li>
     *     <li><b>GET</b> /api/v1/categories/{categoryId}/beers?page=0&amp;size=10&amp;sort=beerName,asc</li>
     * </ul>
     *
     * @param categoryId the UUID of the category whose beers should be retrieved
     * @param pageable   pagination and sorting parameters
     * @return a page of {@link BeerListItemDTO} for beers in the specified category
     */
    @GetMapping(CATEGORIES_BEERS)
    public ResponseEntity<Page<BeerListItemDTO>> getBeersByCategory(
            @PathVariable UUID categoryId,
            @PageableDefault(sort = "beerName", direction = Sort.Direction.ASC) Pageable pageable) {
        log.info("Retrieving beers for category ID: {}", categoryId);
        return ResponseEntity.ok(beerCategoryService.getBeersByCategory(categoryId, pageable));
    }

    /**
     * Add a category to a beer.
     * Example:
     * <ul>
     *     <li><b>POST</b> /api/v1/beers/{beerId}/categories/{categoryId}</li>
     *     <li>Request Body: None</li>
     *     <li>Response: 204 No Content</li>
     * </ul>
     *
     * @param beerId     the UUID of the beer to which the category should be added
     * @param categoryId the UUID of the category to add to the beer
     * @return HTTP 204 No Content on success
     */
    @PostMapping(BEERS_CATEGORIES)
    public ResponseEntity<Void> addCategoryToBeer(
            @PathVariable UUID beerId,
            @PathVariable UUID categoryId) {
        log.info("Adding category ID {} to beer ID {}", categoryId, beerId);
        beerCategoryService.addCategoryToBeer(beerId, categoryId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Remove a category from a beer.
     * Example:
     * <ul>
     *     <li><b>DELETE</b> /api/v1/beers/{beerId}/categories/{categoryId}</li>
     *     <li>Request Body: None</li>
     *     <li>Response: 204 No Content</li>
     * </ul>
     *
     * @param beerId     the UUID of the beer from which the category should be removed
     * @param categoryId the UUID of the category to remove from the beer
     */
    @DeleteMapping(BEERS_CATEGORIES)
    public void deleteCategoryFromBeer(
            @PathVariable UUID beerId,
            @PathVariable UUID categoryId) {
        log.info("Removing category ID {} from beer ID {}", categoryId, beerId);
        beerCategoryService.deleteCategoryFromBeer(beerId, categoryId);
    }
}
