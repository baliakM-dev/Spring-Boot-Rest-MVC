package dev.baliak.beerclient.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Centralized HTTP client configuration for communication
 * with the Beer Store microservice.
 *
 * <p>This configuration defines a single, reusable {@link RestTemplate}
 * bean used for all outbound HTTP calls to the Beer Store API.</p>
 *
 * <p>Responsibilities:
 * <ul>
 *     <li>Defines base URL (rootUri) for cleaner service-layer code</li>
 *     <li>Enforces network timeouts to prevent thread blocking</li>
 *     <li>Standardizes JSON communication headers</li>
 *     <li>Acts as extension point for future cross-cutting concerns</li>
 * </ul>
 *
 * <p>Future improvements may include:
 * <ul>
 *     <li>ClientHttpRequestInterceptor (logging, tracing, correlation IDs)</li>
 *     <li>Authentication headers (OAuth2, JWT, API keys)</li>
 *     <li>Custom error handling strategies</li>
 *     <li>Metrics instrumentation</li>
 * </ul>
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Creates a preconfigured {@link RestTemplate} instance
     * dedicated to Beer Store integration.
     *
     * <p><b>rootUri:</b>
     * Automatically prefixes all relative request paths with the configured base URL.
     * Example:
     * "/api/v1/beers" → "http://localhost:8080/api/v1/beers"
     *
     * <p><b>connectTimeout:</b>
     * Maximum time allowed to establish a TCP connection.
     * Protects the system from hanging connection attempts.
     *
     * <p><b>readTimeout:</b>
     * Maximum time to wait for the response after the connection is established.
     * Prevents blocking threads when downstream service is slow.
     *
     * <p><b>Default headers:</b>
     * Forces JSON-based communication contract for both request and response.
     *
     * @param builder  Spring Boot provided RestTemplateBuilder
     * @param baseUrl  Base URL of the Beer Store service (configured via properties)
     * @return configured RestTemplate bean
     */
    @Bean
    public RestTemplate beerRestTemplate(
            RestTemplateBuilder builder,
            @Value("${beer-store.base-url}") String baseUrl) {

        return builder
                // Prefix all relative URIs with baseUrl
                .rootUri(baseUrl)

                // Fail fast if connection cannot be established
                .connectTimeout(Duration.ofSeconds(3))

                // Prevent long blocking reads
                .readTimeout(Duration.ofSeconds(5))

                // Enforce JSON contract
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)

                .build();
    }
}