package com.restmvc.beer_store.controllers;

import com.restmvc.beer_store.dtos.beer.BeerCreateDTO;
import com.restmvc.beer_store.dtos.beer.BeerResponseDTO;
import com.restmvc.beer_store.services.BeerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * REST controller responsible for managing Beer resources.
 *
 * <p>Provides CRUD operations for Beer entities including
 * optional category associations.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *     <li><b>POST</b> /api/v1/beers – Create a new beer (optionally with category IDs)</li>
 *     <li><b>GET</b> /api/v1/beers – Retrieve a paginated list of beers (including categories)</li>
 *     <li><b>GET</b> /api/v1/beers/{id} – Retrieve beer by ID (including categories)</li>
 *     <li><b>PUT</b> /api/v1/beers/{id} – Fully update beer</li>
 *     <li><b>PATCH</b> /api/v1/beers/{id} – Partially update beer</li>
 *     <li><b>DELETE</b> /api/v1/beers/{id} – Delete beer</li>
 * </ul>
 *
 * <p>All responses follow standard REST conventions and return appropriate HTTP status codes.</p>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public class BeerController {

    public static final String BASE_URL = "/api/v1/beers";
    public static final String BASE_URL_ID = BASE_URL + "/{beerId}";

    private final BeerService beerService;

    /**
     * Create a new beer (optionally with category IDs)
     *
     * @param beerCreateDTO The beer data to create
     * @return status code 201 Created and location of created resource
     */
    @PostMapping(BASE_URL)
    public ResponseEntity<Void> createBeer(@Validated @RequestBody BeerCreateDTO beerCreateDTO) {
        log.info("Creating beer: {}", beerCreateDTO);
        var savedBeer = beerService.createBeer(beerCreateDTO);
        return ResponseEntity.created(URI.create(BASE_URL + "/" + savedBeer)).build();
    }

    /**
     * Retrieve a paginated list of beers (including categories) and optionally filter by name or UPC.
     * Example:
     *
     *  <ul>
     *     <li><b>GET</b> /api/v1/beers?beerName=IPA&upc=1234567890123&showInventoryOnHand=true</li>
     *      <li><b>GET</b> /api/v1/beers?beerName=IPA&showInventoryOnHand=true</li>
     *      <li><b>GET</b> api/v1/beers?page=0&size=10&sort=beerName,desc</li>
     *      <li><b>GET</b> /api/v1/beers?upc=1234567890123&showInventoryOnHand=true</li>
     *  </ul>
     *
     * @param beerName beer name to filter by (optional)
     * @param upc UPC barcode to filter by (optional)
     * @param showInventoryOnHand quantity on hand flag (optional)
     * @param pageable pagination parameters
     * @return a page of beers matching the filter criteria
     */
    @GetMapping(BASE_URL)
    public ResponseEntity<Page<BeerResponseDTO>> getAllBeers(
            @RequestParam(required = false) String beerName,
            @RequestParam(required = false) String upc,
            @RequestParam(required = false) boolean showInventoryOnHand,
            @PageableDefault(sort = "beerName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        log.info("Retrieving beers: beerName={}, upc={}, showInventoryOnHand={}", beerName, upc, showInventoryOnHand);
        return ResponseEntity.ok(beerService.getAllBeers(beerName, upc, showInventoryOnHand, pageable));
    }
}
