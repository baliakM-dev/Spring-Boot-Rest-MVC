package dev.baliak.beerclient.service;

import dev.baliak.beerclient.dto.BeerCreateRequestDTO;
import dev.baliak.beerclient.dto.BeerResponseDTO;
import dev.baliak.beerclient.dto.BeerUpdateRequestDTO;
import dev.baliak.beerclient.dto.PagedResponse;
import dev.baliak.beerclient.exceptions.ResourceAlreadyExistsExceptions;
import dev.baliak.beerclient.exceptions.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import dev.baliak.beerclient.exceptions.BeerServiceUnavailableException;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;


/**
 * Service responsible for communication with the downstream beer-store service.
 *
 * This layer:
 *  - isolates HTTP communication from controllers
 *  - applies resilience patterns (Retry + CircuitBreaker)
 *  - translates downstream HTTP errors into domain-specific exceptions
 *
 * Controllers should never deal with HttpClientErrorException directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BeerRestClientService {

    private final RestClient beerRestClient;

    /**
     * Creates a new beer in the downstream service.
     *
     * Resilience:
     *  - Retry: retries transient failures (timeouts, connection issues)
     *  - CircuitBreaker: prevents cascading failures
     *
     * Business errors (4xx) are propagated.
     * Infrastructure errors trigger fallback → 503.
     */
    @CircuitBreaker(name = "beerService", fallbackMethod = "createBeerFallback")
    @Retry(name = "beerService")
    public URI createBeer(BeerCreateRequestDTO request) {

        var response = beerRestClient.post()
                .uri("/api/v1/beers")
                .body(request)
                .retrieve()
                .toBodilessEntity();

        return response.getHeaders().getLocation();
    }

    /**
     * Retrieves paginated beers from downstream.
     *
     * On infra failure → fallback returns empty page
     * (UI-friendly behavior instead of 503).
     */
    @CircuitBreaker(name = "beerService", fallbackMethod = "getAllBeersFallback")
    @Retry(name = "beerService")
    public PagedResponse<BeerResponseDTO> getAllBeers(String beerName,
                                                      String upc,
                                                      Boolean showInventory,
                                                      Integer page,
                                                      Integer size) {
        int p = page != null ? page : 0;
        int s = size != null ? size : 25;

        return beerRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/beers")
                        .queryParamIfPresent("beerName", Optional.ofNullable(beerName))
                        .queryParamIfPresent("upc", Optional.ofNullable(upc))
                        .queryParamIfPresent("showInventoryOnHand", Optional.ofNullable(showInventory))
                        .queryParam("page", p)
                        .queryParam("size", s)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<PagedResponse<BeerResponseDTO>>() {});
    }

    /**
     * Retrieves beer by ID.
     *
     * 404 → mapped to ResourceNotFoundException
     * Infra failure → mapped to 503
     */
    @CircuitBreaker(name = "beerService", fallbackMethod = "getBeerByIdFallback")
    @Retry(name = "beerService")
    public BeerResponseDTO getBeerById(UUID beerId) {
        return beerRestClient.get()
                .uri("/api/v1/beers/{beerId}", beerId)
                .retrieve()
                .body(BeerResponseDTO.class);
    }

    /**
     * Updates beer by ID.
     *
     * 404 → ResourceNotFoundException
     * 409 → ResourceAlreadyExistsException (conflict)
     * Infra → fallback → 503
     */
    @CircuitBreaker(name = "beerService", fallbackMethod = "updateBeerByIdFallback")
    @Retry(name = "beerService")
    public BeerResponseDTO updateBeerById(UUID beerId, BeerUpdateRequestDTO beerUpdateRequestDTO) {
        return beerRestClient.put()
                .uri("/api/v1/beers/{beerId}", beerId)
                .body(beerUpdateRequestDTO)
                .retrieve()
                .body(BeerResponseDTO.class);
    }

    /**
     * Fallback for update operation.
     *
     * Business errors are re-mapped to domain exceptions.
     * All other failures are treated as infrastructure issues.
     */
    private BeerResponseDTO updateBeerByIdFallback(UUID beerId, BeerUpdateRequestDTO beerUpdateRequestDTO, Throwable t) {
        if (t instanceof HttpClientErrorException.NotFound) {
            throw new ResourceNotFoundException("Beer", "id", beerId.toString());
        } if (t instanceof HttpClientErrorException.Conflict) {
            throw new ResourceAlreadyExistsExceptions("Beer", "id", beerId.toString());
        }
        throw new BeerServiceUnavailableException("Beer service unavailable");
    }

    /**
     * Fallback for getBeerById.
     */
    private BeerResponseDTO getBeerByIdFallback(UUID beerId, Throwable t) {
        if (t instanceof HttpClientErrorException.NotFound) {
            throw new ResourceNotFoundException("Beer", "id", beerId.toString());
        }
        throw new BeerServiceUnavailableException("Beer service unavailable");
    }

    /**
     * Fallback for create operation.
     */
    private URI createBeerFallback(BeerCreateRequestDTO request, Throwable t) {
        throw new BeerServiceUnavailableException(
                "Beer service is currently unavailable. Please try again later."
        );
    }

    /**
     * Fallback for list endpoint.
     *
     * Returns empty page instead of failing the entire request.
     * This is often preferable for UI-driven listing endpoints.
     */
    @SuppressWarnings("unused")
    private PagedResponse<BeerResponseDTO> getAllBeersFallback(
            String beerName, String upc, Boolean showInventory, Integer page, Integer size, Throwable t) {
        return PagedResponse.empty();
    }

}
