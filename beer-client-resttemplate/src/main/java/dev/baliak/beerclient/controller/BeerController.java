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

/**
 * REST controller that exposes Beer CRUD endpoints to clients of this application.
 *
 * <p>This controller is a thin façade: it delegates all business logic
 * and downstream communication to {@link dev.baliak.beerclient.services.BeerRestTemplateService}.
 *
 * <p>Base paths:
 * <ul>
 *   <li>Collection: {@value BASE_URL}</li>
 *   <li>Single resource: {@value BASE_URL_ID}</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BeerController {

    private final BeerRestTemplateService beer;
    public static final String BASE_URL = "/client/beers";
    public static final String BASE_URL_ID = "/client/beers/{beerId}";

    /**
     * Returns a paginated and filtered list of beers.
     *
     * @param beerName          optional name filter (partial match depends on downstream)
     * @param upc               optional UPC filter
     * @param showInventoryOnHand if true, includes inventory quantity in the response
     * @param page              zero-based page index (default 0)
     * @param size              page size (default 25)
     * @return 200 OK with paged beer list; empty page when downstream is unavailable
     */
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

    /**
     * Returns a single beer by its UUID.
     *
     * @param beerId beer identifier
     * @return 200 OK with beer details
     * @throws dev.baliak.beerclient.exceptions.BeerNotFoundException      if the beer does not exist (404)
     * @throws dev.baliak.beerclient.exceptions.ServiceUnavailableException if the downstream is unreachable
     */
    @GetMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> getBeerById(@PathVariable UUID beerId) {
        log.info("Retrieving beer by ID: {}", beerId);
        return ResponseEntity.ok(beer.getBeerById(beerId));
    }

    /**
     * Creates a new beer and returns its location.
     *
     * @param beerCreateRequestDTO validated creation payload
     * @return 201 Created with Location header pointing to the new resource
     * @throws dev.baliak.beerclient.exceptions.BeerAlreadyExistsException  if a beer with the same UPC already exists (409)
     * @throws dev.baliak.beerclient.exceptions.BeerValidationException      if the request body fails validation (400)
     * @throws dev.baliak.beerclient.exceptions.ServiceUnavailableException  if the downstream is unreachable
     */
    @PostMapping(BASE_URL)
    public ResponseEntity<Void> createBeer(@Validated @RequestBody BeerCreateRequestDTO beerCreateRequestDTO) {
        log.info("Creating beer: {}", beerCreateRequestDTO);
        var savedBeer = beer.createBeer(beerCreateRequestDTO);
        return ResponseEntity.created(URI.create(BASE_URL + "/" + savedBeer)).build();
    }

    /**
     * Fully replaces an existing beer (PUT semantics).
     *
     * @param beerId               beer identifier
     * @param beerUpdateRequestDTO validated full replacement payload
     * @return 200 OK with updated beer
     * @throws dev.baliak.beerclient.exceptions.BeerNotFoundException       if the beer does not exist (404)
     * @throws dev.baliak.beerclient.exceptions.BeerValidationException     if the request body fails validation (400)
     * @throws dev.baliak.beerclient.exceptions.ServiceUnavailableException if the downstream is unreachable
     */
    @PutMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> updateBeerById(
            @PathVariable UUID beerId,
            @Validated @RequestBody BeerUpdateRequestDTO beerUpdateRequestDTO) {
        log.info("Updating beer by ID: {}", beerId);
        return ResponseEntity.ok(beer.updateBeerById(beerId, beerUpdateRequestDTO));
    }

    /**
     * Partially updates an existing beer (PATCH semantics).
     * Only non-null fields in the request body are applied.
     *
     * @param beerId               beer identifier
     * @param beerPatchRequestDTO  partial update payload (all fields optional)
     * @return 200 OK with updated beer
     * @throws dev.baliak.beerclient.exceptions.BeerNotFoundException       if the beer does not exist (404)
     * @throws dev.baliak.beerclient.exceptions.ServiceUnavailableException if the downstream is unreachable
     */
    @PatchMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> patchBeerById(
            @PathVariable UUID beerId,
            @Validated @RequestBody BeerPatchRequestDTO beerPatchRequestDTO) {
        log.info("Patching beer by ID: {}", beerId);
        return ResponseEntity.ok(beer.patchBeerById(beerId, beerPatchRequestDTO));
    }
}
