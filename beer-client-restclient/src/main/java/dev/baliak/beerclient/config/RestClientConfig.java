package dev.baliak.beerclient.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Central configuration for RestClient used to communicate with the beer-store service.
 *
 * This class defines:
 *  - base URL configuration
 *  - default JSON headers
 *  - cross-cutting concerns (logging interceptor)
 *
 * All outbound HTTP calls to beer-store go through this bean.
 */
@Configuration
@Slf4j
public class RestClientConfig {

    /**
     * Creates a single RestClient bean configured for beer-store communication.
     *
     * @param baseUrl injected from application.yaml (beer-store.base-url)
     *
     * Why central config?
     * - avoids duplicating base URL across services
     * - ensures consistent headers
     * - provides a single place for adding timeouts, auth, tracing, etc.
     */
    @Bean
    public RestClient restClient(@Value("${beer-store.base-url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptors(interceptors -> {
                    interceptors.add(loggingInterceptor());
                })
                .build();
    }

    /**
     * Simple logging interceptor for outbound HTTP calls.
     *
     * Logs:
     *  - HTTP method + URI before execution
     *  - Response status after execution
     *
     * Useful for:
     *  - debugging downstream communication
     *  - observing retry/circuit breaker behavior
     *
     * NOTE:
     * In production, consider structured logging or correlation IDs.
     */
    private ClientHttpRequestInterceptor loggingInterceptor() {
        return (request, body, execution) -> {
            log.info(">>> {} {}", request.getMethod(), request.getURI());
            var response = execution.execute(request, body);
            log.info("<<< HTTP {}", response.getStatusCode());
            return response;
        };
    }
}
