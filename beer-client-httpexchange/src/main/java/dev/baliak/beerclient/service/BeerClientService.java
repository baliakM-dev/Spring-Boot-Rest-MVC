package dev.baliak.beerclient.service;

import dev.baliak.beerclient.client.BeerClientApi;
import dev.baliak.beerclient.dto.BeerCreateRequestDTO;
import dev.baliak.beerclient.dto.BeerResponseDTO;
import dev.baliak.beerclient.dto.PagedResponse;
import dev.baliak.beerclient.exceptions.BeerServiceUnavailableException;
import dev.baliak.beerclient.exceptions.ResourceAlreadyExistsExceptions;
import dev.baliak.beerclient.exceptions.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URI;
import java.util.UUID;

/**
 * Service layer responsible for all beer-related operations against the remote beer-store.
 *
 * <p>Every public method is protected by two Resilience4j patterns applied as AOP aspects:
 * <ol>
 *   <li>{@code @Retry} — outer aspect, retries on network errors up to {@code max-attempts} times
 *       with a configured pause between each attempt</li>
 *   <li>{@code @CircuitBreaker} — inner aspect, tracks the failure rate over a sliding window
 *       and opens the circuit when the threshold is exceeded, routing subsequent calls directly
 *       to the fallback without hitting the network</li>
 * </ol>
 * The annotation order matters: {@code @Retry} must be declared first (outermost) so that
 * retry attempts pass through the circuit breaker, not around it.</p>
 *
 * <p><b>Fallback rules enforced by Resilience4j (via reflection):</b>
 * <ul>
 *   <li>Fallback method must have the <b>same parameter types</b> as the guarded method
 *       plus a trailing {@code Throwable} parameter.</li>
 *   <li>Fallback method must have the <b>same return type</b> as the guarded method.</li>
 *   <li>Fallback must never return {@code null} — always return a safe value or throw
 *       a domain exception handled by {@code GlobalExceptionHandler}.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BeerClientService {

    private final BeerClientApi beerClientApi;

    /**
     * Retrieves a paginated and optionally filtered list of beers from the remote beer-store.
     *
     * @param beerName            optional filter by beer name
     * @param upc                 optional filter by UPC barcode
     * @param showInventoryOnHand when {@code true} the server includes stock quantity
     * @param page                zero-based page index
     * @param size                page size
     * @return paginated beer list, or an empty page when the server is unavailable (via fallback)
     */
    @Retry(name = "beerClient")
    @CircuitBreaker(name = "beerClient", fallbackMethod = "getAllBeersFallback")
    public PagedResponse<BeerResponseDTO> getAllBeers(
            String beerName,
            String upc,
            Boolean showInventoryOnHand,
            Integer page,
            Integer size) {
        return beerClientApi.getAllBeers(beerName, upc, showInventoryOnHand, page, size);
    }

    /**
     * Fallback for {@link #getAllBeers} — invoked when all retry attempts are exhausted
     * or the CircuitBreaker is OPEN.
     *
     * <p>Returns an empty {@link PagedResponse} so the caller receives a valid (albeit empty)
     * response rather than a 5xx error. Appropriate for list endpoints where "no results"
     * is an acceptable degraded state.</p>
     */
    PagedResponse<BeerResponseDTO> getAllBeersFallback(
            String beerName,
            String upc,
            Boolean showInventoryOnHand,
            Integer page,
            Integer size,
            Throwable ex) {
        extracted(ex);
        return PagedResponse.empty();
    }

    /**
     * Retrieves a single beer by its UUID from the remote beer-store.
     *
     * @param beerId UUID of the beer to retrieve
     * @return beer details
     * @throws ResourceNotFoundException       if the server responds with 404
     * @throws BeerServiceUnavailableException if the server is unreachable or the circuit is OPEN
     */
    @Retry(name = "beerClient")
    @CircuitBreaker(name = "beerClient", fallbackMethod = "getBeerByIdFallback")
    public BeerResponseDTO getBeerById(UUID beerId) {
        return beerClientApi.getBeerById(beerId);
    }

    /**
     * Fallback for {@link #getBeerById} — invoked when all retry attempts are exhausted
     * or the CircuitBreaker is OPEN.
     *
     * <p>Distinguishes between a business error (404 — beer does not exist) and an
     * infrastructure error (server down, circuit open) so the caller receives the
     * appropriate HTTP status via {@code GlobalExceptionHandler}.</p>
     */
    BeerResponseDTO getBeerByIdFallback(UUID beerId, Throwable ex) {
        if (ex instanceof HttpClientErrorException.NotFound) {
            log.warn("Beer with ID {} not found. Returning empty response.", beerId);
            throw new ResourceNotFoundException("Beer", "id", beerId.toString());
        }
        extracted(ex);
        throw new BeerServiceUnavailableException("Beer service unavailable", ex.getMessage());
    }

    /**
     * Creates a new beer on the remote beer-store and returns its generated UUID.
     *
     * <p>The server responds with {@code 201 Created} and an empty body — the new resource's
     * UUID is carried in the {@code Location} response header
     * (e.g. {@code Location: /api/v1/beers/{uuid}}). This method extracts the UUID from
     * the last path segment of that header.</p>
     *
     * @param beerCreateRequestDTO request body with beer details
     * @return UUID of the newly created beer
     * @throws BeerServiceUnavailableException if the server does not return a Location header,
     *                                         is unreachable, or the circuit is OPEN
     * @throws ResourceAlreadyExistsExceptions if the server responds with 409 Conflict
     */
    @Retry(name = "beerClient")
    @CircuitBreaker(name = "beerClient", fallbackMethod = "createBeerFallback")
    public UUID createBeer(BeerCreateRequestDTO beerCreateRequestDTO) {
        // call remote API — returns 201 Created with empty body and Location header
        var response = beerClientApi.createBeer(beerCreateRequestDTO);
        // extract Location header — must be present, otherwise the server behaved unexpectedly
        URI location = response.getHeaders().getLocation();
        if (location == null) throw new BeerServiceUnavailableException("Server did not return Location header after beer creation");
        // parse the UUID from the last path segment: /api/v1/beers/{uuid}
        String path = location.getPath();
        return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    }

    /**
     * Fallback for {@link #createBeer} — invoked when all retry attempts are exhausted
     * or the CircuitBreaker is OPEN.
     *
     * <p>A 409 Conflict is a business error (not retryable) and is re-thrown immediately as
     * {@link ResourceAlreadyExistsExceptions}. Any other cause is treated as an infrastructure
     * failure and thrown as {@link BeerServiceUnavailableException}.</p>
     */
    UUID createBeerFallback(BeerCreateRequestDTO beerCreateRequestDTO, Throwable ex) {
        if (ex instanceof HttpClientErrorException.Conflict) {
            throw new ResourceAlreadyExistsExceptions("Beer", "name", beerCreateRequestDTO.beerName());
        }
        extracted(ex);
        throw new BeerServiceUnavailableException("Beer store server is unavailable", ex.getMessage());
    }

    /**
     * Replaces an existing beer's data with the supplied values on the remote beer-store.
     *
     * @param beerId               UUID of the beer to update
     * @param beerCreateRequestDTO request body with updated beer details
     * @return updated beer details
     * @throws ResourceNotFoundException       if the server responds with 404
     * @throws ResourceAlreadyExistsExceptions if the server responds with 409 Conflict
     * @throws BeerServiceUnavailableException if the server is unreachable or the circuit is OPEN
     */
    @Retry(name = "beerClient")
    @CircuitBreaker(name = "beerClient", fallbackMethod = "updateBeerByIdFallback")
    public BeerResponseDTO updateBeerById(
            UUID beerId,
            BeerCreateRequestDTO beerCreateRequestDTO) {
        return beerClientApi.updateBeerById(beerId, beerCreateRequestDTO);
    }

    /**
     * Fallback for {@link #updateBeerById} — invoked when all retry attempts are exhausted
     * or the CircuitBreaker is OPEN.
     *
     * <p>Handles three distinct cases:
     * <ul>
     *   <li>404 — beer with the given ID does not exist → {@link ResourceNotFoundException}</li>
     *   <li>409 — name conflicts with another existing beer → {@link ResourceAlreadyExistsExceptions}</li>
     *   <li>anything else — server unreachable or circuit open → {@link BeerServiceUnavailableException}</li>
     * </ul>
     * </p>
     */
    BeerResponseDTO updateBeerByIdFallback(UUID beerId, BeerCreateRequestDTO beerCreateRequestDTO, Throwable ex) {
        if (ex instanceof HttpClientErrorException.NotFound) {
            throw new ResourceNotFoundException("Beer", "id", beerId.toString());
        }
        if (ex instanceof HttpClientErrorException.Conflict) {
            throw new ResourceAlreadyExistsExceptions("Beer", "name", beerCreateRequestDTO.beerName());
        }
        extracted(ex);
        throw new BeerServiceUnavailableException("Beer store server is unavailable", ex.getMessage());
    }

    /**
     * Logs a warning when the beer-store is unreachable or the CircuitBreaker blocks the call.
     *
     * @param ex the exception caught by the fallback
     */
    private static void extracted(Throwable ex) {
        log.warn("Beer store server is unavailable. Returning empty response. Cause: {}", ex.getMessage());
    }

}
