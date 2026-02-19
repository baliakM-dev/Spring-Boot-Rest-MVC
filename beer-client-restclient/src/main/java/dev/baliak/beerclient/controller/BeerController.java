package dev.baliak.beerclient.controller;

import dev.baliak.beerclient.dto.BeerCreateRequestDTO;
import dev.baliak.beerclient.dto.BeerResponseDTO;
import dev.baliak.beerclient.dto.BeerUpdateRequestDTO;
import dev.baliak.beerclient.dto.PagedResponse;
import dev.baliak.beerclient.service.BeerRestClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller exposing beer management endpoints.
 *
 * <p>Acts as a thin delegation layer: it validates incoming requests
 * and forwards them to {@link BeerRestClientService}, which handles
 * all downstream HTTP communication with the beer-store service.</p>
 *
 * <p>Base path: {@code /client/beers}</p>
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class BeerController {

    public static final String BASE_URL = "/client/beers";
    public static final String BASE_URL_ID = "/client/beers/{beerId}";
    private final BeerRestClientService beerService;

    /**
     * Creates a new beer by forwarding the request to the beer-store service.
     *
     * @param beerCreateRequestDTO validated request body
     * @return 201 Created with Location header pointing to the new resource
     */
    @PostMapping(BASE_URL)
    public ResponseEntity<Void> createBeer(@Valid @RequestBody BeerCreateRequestDTO beerCreateRequestDTO) {
        log.info("Creating beer: {}", beerCreateRequestDTO);
        var savedBeer = beerService.createBeer(beerCreateRequestDTO);
        return ResponseEntity.created(URI.create(BASE_URL + "/" + savedBeer)).build();
    }

    /**
     * Returns a paginated list of beers with optional filtering.
     *
     * @param beerName           optional filter by beer name
     * @param upc                optional filter by UPC barcode
     * @param showInventoryOnHand optional flag to include inventory count in response
     * @param page               page index (0-based), defaults to 0
     * @param size               page size, defaults to 25
     * @return 200 OK with paginated beer data
     */
    @GetMapping(BASE_URL)
    public ResponseEntity<PagedResponse<BeerResponseDTO>> getAllBeers(
            @RequestParam(required = false) String beerName,
            @RequestParam(required = false) String upc,
            @RequestParam(required = false) Boolean showInventoryOnHand,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "25") Integer size) {

        return ResponseEntity.ok(
                beerService.getAllBeers(beerName, upc, showInventoryOnHand, page, size)
        );
    }

    /**
     * Returns a single beer by its UUID.
     *
     * @param beerId beer identifier
     * @return 200 OK with beer data, or 404 if not found
     */
    @GetMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> getBeerById(@PathVariable UUID beerId) {
         return ResponseEntity.ok(beerService.getBeerById(beerId));
    }

    /**
     * Updates an existing beer by its UUID.
     *
     * @param beerId               beer identifier
     * @param beerUpdateRequestDTO validated update payload
     * @return 200 OK with updated beer data, or 404 if not found
     */
    @PutMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> updateBeer(@PathVariable UUID beerId,
                                                      @Valid @RequestBody BeerUpdateRequestDTO beerUpdateRequestDTO) {
        return ResponseEntity.ok(beerService.updateBeerById(beerId, beerUpdateRequestDTO));
    }
}
