package dev.baliak.beerclient.services;

import dev.baliak.beerclient.controller.BeerController;
import dev.baliak.beerclient.dto.*;
import dev.baliak.beerclient.exceptions.BeerAlreadyExistsException;
import dev.baliak.beerclient.exceptions.BeerNotFoundException;
import dev.baliak.beerclient.exceptions.BeerValidationException;
import dev.baliak.beerclient.exceptions.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Service layer for Beer Store integration using RestTemplate.
 *
 * <p>This class encapsulates HTTP calls to the Beer Store API and applies resilience patterns:
 * <ul>
 *   <li><b>Retry</b> - to re-attempt transient failures (timeouts, connection resets, 5xx)</li>
 *   <li><b>Circuit Breaker</b> - to fail fast when the downstream service is consistently failing</li>
 * </ul>
 *
 * <p>It also maps downstream HTTP errors to local domain exceptions using {@link ProblemDetail}
 * (Spring's RFC 7807-style error payload).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BeerRestTemplateService {

    private final RestTemplate beerRestTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    /**
     * Name used to resolve CircuitBreaker/Retry instances from registries.
     * Must match your Resilience4j configuration (yaml/properties) if you customize it.
     */
    private static final String CB_NAME = "beerService";

    /**
     * Executes a supplier within a resilience boundary (Retry + CircuitBreaker).
     *
     * <h3>Behavior rules</h3>
     * <ul>
     *   <li><b>HTTP errors (4xx/5xx):</b> captured as {@link HttpStatusCodeException} and translated into domain exceptions.</li>
     *   <li><b>Circuit breaker OPEN:</b> returns fallback immediately (fail fast).</li>
     *   <li><b>Technical failures:</b> connection refused, timeouts, DNS issues -> fallback.</li>
     * </ul>
     *
     * <p><b>Important:</b> We translate HTTP errors into domain exceptions because:
     * <ul>
     *   <li>4xx are business/validation problems (should NOT trigger fallback)</li>
     *   <li>5xx can be either business or transient, but you typically still want consistent mapping</li>
     * </ul>
     */
    private <T> T withResilience(Supplier<T> call, Supplier<T> fallback) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(CB_NAME);
        Retry retry = retryRegistry.retry(CB_NAME);

        // Decorate call with Retry first, then protect it with CircuitBreaker.
        // Retry re-attempts transient failures; CircuitBreaker tracks outcomes and can OPEN to prevent storms.
        Supplier<T> decorated = CircuitBreaker.decorateSupplier(cb, Retry.decorateSupplier(retry, call));

        try {
            return decorated.get();

        } catch (HttpStatusCodeException ex) {
            // Downstream responded with an HTTP status (client/server error).
            // This is an "expected" API response -> translate into a domain exception.
            throw handleException(ex);

        } catch (CallNotPermittedException ex) {
            // CircuitBreaker is OPEN -> fail fast, do not call downstream.
            log.warn("CircuitBreaker '{}' is OPEN - returning fallback", CB_NAME);
            return fallback.get();

        } catch (Exception ex) {
            // Technical/network failure (timeouts, connection refused, broken pipe, etc.).
            // This is where fallback is appropriate.
            log.error("Beer service call failed (technical), using fallback. Error: {}", ex.getMessage());
            return fallback.get();
        }
    }

    /**
     * Fetches a paginated and filtered list of beers.
     *
     * <p><b>Fallback:</b> returns an empty page so the UI can still render without blowing up
     * when the downstream service is down.</p>
     */
    public PagedResponse<BeerResponseDTO> getAllBeers(
            String beerName, String upc, Boolean showInventory, Integer page, Integer size) {
        log.debug("getAllBeers called with params: beerName={}, upc={}, showInventory={}, page={}, size={}", beerName, upc, showInventory, page, size);
        return withResilience(
                () -> {
                    var url = UriComponentsBuilder.fromPath("/api/v1/beers")
                            .queryParamIfPresent("beerName", Optional.ofNullable(beerName))
                            .queryParamIfPresent("upc", Optional.ofNullable(upc))
                            .queryParamIfPresent("showInventoryOnHand", Optional.ofNullable(showInventory))
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .queryParam("sort", "beerName,asc")
                            .toUriString();
                    var response = beerRestTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            null,
                            new ParameterizedTypeReference<PagedResponse<BeerResponseDTO>>() {
                            }
                    );
                    return response.getBody();
                },
                () -> {
                    log.warn("Returning empty page as fallback");
                    return PagedResponse.empty();
                }
        );
    }

    /**
     * Fetches a single Beer by ID.
     *
     * <p><b>Fallback:</b> throws {@link ServiceUnavailableException} because we cannot fabricate
     * a single resource (unlike paging list where empty result is acceptable).</p>
     */
    public BeerResponseDTO getBeerById(UUID beerId) {
        return withResilience(
                () -> beerRestTemplate.getForObject("/api/v1/beers/{beerId}", BeerResponseDTO.class, beerId),
                () -> {
                    throw new ServiceUnavailableException("Beer service unavailable - cannot fetch beer: " + beerId);
                }
        );
    }

    /**
     * Creates a new Beer.
     *
     * <p>409 Conflict is mapped to {@link BeerAlreadyExistsException} by {@link #handleException(HttpStatusCodeException)}.</p>
     *
     * <p><b>Fallback:</b> throws {@link ServiceUnavailableException} since creation cannot continue without downstream.</p>
     *
     * @return URI of the created resource (Location header)
     */
    public URI createBeer(BeerCreateRequestDTO request) {
        return withResilience(
                () -> {
                    var response = beerRestTemplate.postForEntity("/api/v1/beers", request, Void.class);
                    return response.getHeaders().getLocation();
                },
                () -> {
                    throw new ServiceUnavailableException("Beer service unavailable - cannot create beer");
                }
        );
    }

    /**
     * Updates an existing Beer by ID (PUT).
     *
     * <p>Typical mappings:</p>
     * <ul>
     *   <li>404 -> {@link BeerNotFoundException}</li>
     *   <li>400 -> {@link BeerValidationException}</li>
     *   <li>409 -> {@link BeerAlreadyExistsException}</li>
     * </ul>
     */
    public BeerResponseDTO updateBeerById(UUID beerId, BeerUpdateRequestDTO request) {
        return withResilience(
                () -> {
                    var response = beerRestTemplate.exchange(
                            "/api/v1/beers/{beerId}",
                            HttpMethod.PUT,
                            new HttpEntity<>(request),
                            BeerResponseDTO.class,
                            beerId
                    );
                    return response.getBody();
                },
                () -> {
                    throw new ServiceUnavailableException("Cannot update beer - service unavailable");
                }
        );
    }

    /**
     * Partially updates an existing Beer by ID (PATCH).
     *
     * <p>Note: PATCH support depends on downstream controller + HTTP client support.
     * If downstream does not expose PATCH, you'll get 405 which is translated by {@link #handleException(HttpStatusCodeException)}.</p>
     */
    public BeerResponseDTO patchBeerById(UUID beerId, BeerPatchRequestDTO request) {
        return withResilience(
                () -> {
                    var response = beerRestTemplate.exchange(
                            "/api/v1/beers/{beerId}",
                            HttpMethod.PATCH,
                            new HttpEntity<>(request),
                            BeerResponseDTO.class,
                            beerId
                    );
                    return response.getBody();
                },
                () -> {
                    throw new ServiceUnavailableException("Cannot patch beer - service unavailable");
                }
        );
    }

    /**
     * Translates downstream HTTP errors into local domain exceptions.
     *
     * <p>We try to parse {@link ProblemDetail} (RFC 7807 style) from the response body.
     * If parsing fails or body is not ProblemDetail, we fall back to the exception message.</p>
     *
     * <p><b>Rule of thumb:</b></p>
     * <ul>
     *   <li>409 -> resource already exists</li>
     *   <li>400 -> validation / malformed request</li>
     *   <li>404 -> not found</li>
     *   <li>others -> rethrow original exception to keep details for debugging</li>
     * </ul>
     */
    private RuntimeException handleException(HttpStatusCodeException ex) {

        // Try to parse RFC 9457 / ProblemDetail from the downstream API (Spring 6+ commonly returns this).
        ProblemDetail pd = ex.getResponseBodyAs(ProblemDetail.class);
        String detail = (pd != null && pd.getDetail() != null) ? pd.getDetail() : ex.getMessage();

        // 409 Conflict -> resource already exists
        if (ex.getStatusCode() == HttpStatus.CONFLICT) {
            return new BeerAlreadyExistsException(detail, ex);
        }

        // 400 Bad Request -> validation / malformed request (client-side representation)
        if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
            // If your API returns validation errors in a custom JSON shape, parse it here instead.
            return new BeerValidationException(detail, ex);
        }

        // 404 Not Found (optional, but very common)
        if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
            return new BeerNotFoundException(detail, ex);
        }

        // Anything else: keep original (preserves status + response body for debugging).
        return ex;
    }

}
