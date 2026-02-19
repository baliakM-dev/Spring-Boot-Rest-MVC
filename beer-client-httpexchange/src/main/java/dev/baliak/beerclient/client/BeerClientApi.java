package dev.baliak.beerclient.client;

import dev.baliak.beerclient.dto.BeerCreateRequestDTO;
import dev.baliak.beerclient.dto.BeerResponseDTO;
import dev.baliak.beerclient.dto.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.UUID;

/**
 * Declarative HTTP client for the remote beer-store API.
 *
 * <p>Uses Spring's {@code @HttpExchange} mechanism (introduced in Spring 6) as a
 * type-safe, annotation-driven alternative to manually building {@code RestClient}
 * calls or using Feign.</p>
 *
 * <p>All methods communicate with the base path {@code /api/v1/beers}.
 * The concrete proxy implementation is generated at startup by
 * {@code RestClientHttpServiceGroupConfigurer} defined in {@code BeerClientConfig}.</p>
 *
 * <p><b>Important:</b> Path variables in URL templates (e.g. {@code /{beerId}}) require
 * {@code @PathVariable}. Using {@code @RequestParam} instead causes
 * {@code IllegalArgumentException: Map has no value for 'beerId'} at runtime.</p>
 */
@HttpExchange(url = "/api/v1/beers", accept = "application/json")
public interface BeerClientApi {

    /**
     * Retrieves a paginated list of beers, optionally filtered by name or UPC.
     *
     * @param beerName          optional filter by beer name (partial match supported by the server)
     * @param upc               optional filter by UPC barcode
     * @param showInventoryOnHand when {@code true} the server includes stock quantity in the response
     * @param page              zero-based page index (default: 0)
     * @param size              page size (default: 20)
     * @return paginated response containing matching beers
     */
    @GetExchange
    PagedResponse<BeerResponseDTO> getAllBeers(
            @RequestParam(required = false) String beerName,
            @RequestParam(required = false) String upc,
            @RequestParam(required = false) Boolean showInventoryOnHand,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size
    );

    /**
     * Retrieves a single beer by its unique identifier.
     *
     * @param beerId UUID of the beer to retrieve
     * @return beer details
     */
    @GetExchange(url = "/{beerId}")
    BeerResponseDTO getBeerById(@PathVariable UUID beerId);

    /**
     * Creates a new beer on the remote server.
     *
     * <p>The server returns {@code 201 Created} with an <b>empty body</b> and the newly
     * created beer's UUID embedded in the {@code Location} response header:
     * <pre>Location: /api/v1/beers/{uuid}</pre>
     * The return type is therefore {@code ResponseEntity<Void>} so the caller can
     * extract the UUID from the header. Using a plain {@code UUID} return type would
     * yield {@code null} because the response body is empty.</p>
     *
     * @param beerCreateRequestDTO validated request body with beer details
     * @return 201 response whose {@code Location} header contains the new beer's URI
     */
    @PostExchange
    ResponseEntity<Void> createBeer(@Valid @RequestBody BeerCreateRequestDTO beerCreateRequestDTO);

    /**
     * Replaces an existing beer's data with the supplied values.
     *
     * @param beerId               UUID of the beer to update
     * @param beerCreateRequestDTO validated request body with updated beer details
     * @return updated beer details
     */
    @PutExchange(url = "/{beerId}")
    BeerResponseDTO updateBeerById(@PathVariable UUID beerId, @RequestBody BeerCreateRequestDTO beerCreateRequestDTO);
}
