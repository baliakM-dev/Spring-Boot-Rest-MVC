package com.restmvc.beer_store.controllers;

import com.restmvc.beer_store.dtos.beer.BeerCreateRequestDTO;
import com.restmvc.beer_store.dtos.beer.BeerPatchRequestDTO;
import com.restmvc.beer_store.dtos.beer.BeerResponseDTO;
import com.restmvc.beer_store.dtos.beer.BeerUpdateRequestDTO;
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
import java.util.UUID;

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
     * @param beerCreateRequestDTO The beer data to create
     * @return status code 201 Created and location of created resource
     */
    @PostMapping(BASE_URL)
    public ResponseEntity<Void> createBeer(@Validated @RequestBody BeerCreateRequestDTO beerCreateRequestDTO) {
        log.info("Creating beer: {}", beerCreateRequestDTO);
        var savedBeer = beerService.createBeer(beerCreateRequestDTO);
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
            @RequestParam(required = false) Boolean showInventoryOnHand,
            @PageableDefault(sort = "beerName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        log.info("Retrieving beers: beerName={}, upc={}, showInventoryOnHand={}", beerName, upc, showInventoryOnHand);
        return ResponseEntity.ok(beerService.getAllBeers(beerName, upc, showInventoryOnHand, pageable));
    }

    /**
     * Retrieve a beer by ID (including categories)
     * Example:
     * <ul>
     *     <li><b>GET</b> /api/v1/beers/{beerId} </li>
     * </ul>
     * @param beerId beer id to retrieve
     * @return ({@link BeerResponseDTO})
     */
    @GetMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> getBeerById(@PathVariable UUID beerId) {
        log.info("Retrieving beer by ID: {}", beerId);
        return ResponseEntity.ok(beerService.getBeerById(beerId));
    }

    /**
     * Update a beer by ID
     * Example:
     *
     * <ul>
     *     <li><b>PUT</b> /api/v1/beers/{beerId} </li>
     * </ul>
     *
     * @param beerId beer id to retrieve
     * @param beerUpdateRequestDTO data to update the beer with
     * @return ({@link BeerResponseDTO})
     */
    @PutMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> updateBeerById(
            @PathVariable UUID beerId,
            @Validated @RequestBody BeerUpdateRequestDTO beerUpdateRequestDTO) {
        log.info("Updating beer by ID: {}", beerId);
        return ResponseEntity.ok(beerService.updateBeerById(beerId, beerUpdateRequestDTO));
    }

    /**
     * Patch a beer by ID.
     * Example:
     * <ul>
     *     <li><b>PATCH</b> /api/v1/beers/{beerId} </li>
     * </ul>
     *
     * @param beerId beer id to retrieve
     * @param beerPatchRequestDTO data to patch the beer with
     * @return ({@link BeerResponseDTO})
     */
    @PatchMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> patchBeerById(
            @PathVariable UUID beerId,
            @Validated @RequestBody BeerPatchRequestDTO beerPatchRequestDTO) {
        log.info("Patching beer by ID: {}", beerId);
        return ResponseEntity.ok(beerService.patchBeerById(beerId, beerPatchRequestDTO));
    }
}
