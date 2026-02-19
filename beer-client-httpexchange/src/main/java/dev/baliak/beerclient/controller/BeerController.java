package dev.baliak.beerclient.controller;

import dev.baliak.beerclient.dto.BeerCreateRequestDTO;
import dev.baliak.beerclient.dto.BeerResponseDTO;
import dev.baliak.beerclient.dto.PagedResponse;
import dev.baliak.beerclient.service.BeerClientService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller that exposes the beer-store API through this service.
 *
 * <p>Acts as a thin facade: it delegates all business logic and remote calls to
 * {@link BeerClientService}, which applies Resilience4j patterns (Retry,
 * CircuitBreaker, Fallback) before reaching the downstream beer-store server.</p>
 *
 * <p>Base path: {@code /client/beers}</p>
 */
@Slf4j
@RestController
public class BeerController {

    public static final String BASE_URL = "/client/beers";
    public static final String BASE_URL_ID = "/client/beers/{beerId}";
    private final BeerClientService beerClientService;

    public BeerController(BeerClientService beerClientService) {
        this.beerClientService = beerClientService;
    }

    /**
     * Returns a paginated list of beers with optional filters.
     *
     * @param beerName            optional filter by beer name
     * @param upc                 optional filter by UPC barcode
     * @param showInventoryOnHand when {@code true} includes stock quantity in the response
     * @param page                zero-based page index (default: 0)
     * @param size                page size (default: 25)
     * @return {@code 200 OK} with paginated beer list, or {@code 503} if the server is unavailable
     */
    @GetMapping(BASE_URL)
    public ResponseEntity<PagedResponse<BeerResponseDTO>> getAllBeers(
            @RequestParam(required = false) String beerName,
            @RequestParam(required = false) String upc,
            @RequestParam(required = false) Boolean showInventoryOnHand,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "25") Integer size) {

        return ResponseEntity.ok(
                beerClientService.getAllBeers(beerName, upc, showInventoryOnHand, page, size)
        );
    }

    /**
     * Returns a single beer by its UUID.
     *
     * @param beerId UUID of the requested beer
     * @return {@code 200 OK} with beer details,
     *         {@code 404} if not found, or {@code 503} if the server is unavailable
     */
    @GetMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> getBeerById(@PathVariable UUID beerId) {
        return ResponseEntity.ok(beerClientService.getBeerById(beerId));
    }

    /**
     * Creates a new beer and returns its location.
     *
     * <p>On success the response contains no body. The {@code Location} header points
     * to the newly created resource: {@code /client/beers/{uuid}}</p>
     *
     * @param beerCreateRequestDTO validated request body with beer details
     * @return {@code 201 Created} with {@code Location} header,
     *         {@code 409} if a beer with the same name already exists,
     *         or {@code 503} if the server is unavailable
     */
    @PostMapping(BASE_URL)
    public ResponseEntity<Void> createBeer(@Valid @RequestBody BeerCreateRequestDTO beerCreateRequestDTO) {
        log.info("Creating beer: {}", beerCreateRequestDTO);
        var savedBeer = beerClientService.createBeer(beerCreateRequestDTO);
        return ResponseEntity.created(URI.create(BASE_URL + "/" + savedBeer)).build();
    }

    /**
     * Replaces an existing beer's data with the supplied values.
     *
     * @param beerId               UUID of the beer to update
     * @param beerCreateRequestDTO validated request body with updated beer details
     * @return {@code 200 OK} with updated beer details,
     *         {@code 404} if not found,
     *         {@code 409} if the new name conflicts with an existing beer,
     *         or {@code 503} if the server is unavailable
     */
    @PutMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> updateBeerById(@PathVariable UUID beerId, @Valid @RequestBody BeerCreateRequestDTO beerCreateRequestDTO) {
        return ResponseEntity.ok(beerClientService.updateBeerById(beerId, beerCreateRequestDTO));
    }
}