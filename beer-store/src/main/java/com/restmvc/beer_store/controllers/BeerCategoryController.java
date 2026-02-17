package com.restmvc.beer_store.controllers;

import com.restmvc.beer_store.dtos.beerCategory.BeerListItemDTO;
import com.restmvc.beer_store.dtos.beerCategory.CategoryListItemDTO;
import com.restmvc.beer_store.services.BeerCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * BEER CATEGORY CONTROLLER
 * <p>
 * POST   /api/v1/beers/{beerId}/categories/{catId}  # Add category
 * DELETE /api/v1/beers/{beerId}/categories/{catId}  # Remove category
 * PUT    /api/v1/beers/{beerId}/categories          # Set all (body: [uuid1, uuid2])
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public class BeerCategoryController {

    private final BeerCategoryService beerCategoryService;

    public static final String CATEGORIES_BASE_URL = "/api/v1/categories";
    public static final String CATEGORIES_BEERS = CATEGORIES_BASE_URL + "/{categoryId}" + "/beers";
    public static final String BEERS_BASE_URL = "/api/v1/beers";
    public static final String BEERS_CATEGORIES = BEERS_BASE_URL + "/{beerId}" + "/categories";

    /**
     * Get all beers for a given category.
     * Example:
     *
     *  <ul>
     *      <li><b>GET</b> /api/v1/categories/{categoryId}/beers </li>
     *  </ul>
     *
     * @param categoryId category ID for which to retrieve beers
     * @param pageable pagination parameters
     * @return a page of beers
     */
    @GetMapping(CATEGORIES_BEERS)
    public ResponseEntity<Page<BeerListItemDTO>> getBeersByCategory(
            @PathVariable UUID categoryId,
            @PageableDefault(sort = "beerName", direction = Sort.Direction.ASC) Pageable pageable) {
        log.info("Retrieving beers for category ID: {}", categoryId);
        return ResponseEntity.ok(beerCategoryService.getBeersByCategory(categoryId, pageable));
    }


//    POST   /api/v1/beers/{beerId}/categories/{categoryId}  // Pridaj kategóriu
//    DELETE /api/v1/beers/{beerId}/categories/{categoryId}  // Odstráň kategóriu
//    PUT    /api/v1/beers/{beerId}/categories     // Nastav všetky (body: [uuid1, uuid2])
}
