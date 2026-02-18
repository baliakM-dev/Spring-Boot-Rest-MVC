package dev.baliak.beerclient.controller;

import dev.baliak.beerclient.dto.*;
import dev.baliak.beerclient.services.BeerRestTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class BeerController {

    private final BeerRestTemplateService beer;
    public static final String BASE_URL = "/client/beers";
    public static final String BASE_URL_ID = "/client/beers/{beerId}";

    @GetMapping(BASE_URL)
    public ResponseEntity<PagedResponse<BeerResponseDTO>> getAllBeers(
            @RequestParam(required = false) String beerName,
            @RequestParam(required = false) String upc,
            @RequestParam(required = false) Boolean showInventoryOnHand,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "25") Integer size
    ) {
        log.info("Retrieving beers: beerName={}, upc={}, showInventoryOnHand={}", beerName, upc, showInventoryOnHand);
        return ResponseEntity.ok(beer.getAllBeers(beerName, upc, showInventoryOnHand, page, size));
    }

    @GetMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> getBeerById(@PathVariable UUID beerId) {
        log.info("Retrieving beer by ID: {}", beerId);
        return ResponseEntity.ok(beer.getBeerById(beerId));
    }

    @PostMapping(BASE_URL)
    public ResponseEntity<Void> createBeer(@Validated @RequestBody BeerCreateRequestDTO beerCreateRequestDTO) {
        log.info("Creating beer: {}", beerCreateRequestDTO);
        var savedBeer = beer.createBeer(beerCreateRequestDTO);
        return ResponseEntity.created(URI.create(BASE_URL + "/" + savedBeer)).build();
    }

    @PutMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> updateBeerById(
            @PathVariable UUID beerId,
            @Validated @RequestBody BeerUpdateRequestDTO beerUpdateRequestDTO) {
        log.info("Updating beer by ID: {}", beerId);
        return ResponseEntity.ok(beer.updateBeerById(beerId, beerUpdateRequestDTO));
    }

    @PatchMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> patchBeerById(
            @PathVariable UUID beerId,
            @Validated @RequestBody BeerPatchRequestDTO beerPatchRequestDTO) {
        log.info("Patching beer by ID: {}", beerId);
        return ResponseEntity.ok(beer.patchBeerById(beerId, beerPatchRequestDTO));
    }
}
