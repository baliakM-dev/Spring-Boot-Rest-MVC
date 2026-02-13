package com.restmvc.beer_store.controllers;

import com.restmvc.beer_store.dtos.BeerCreateDTO;
import com.restmvc.beer_store.services.BeerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
}
