package dev.baliak.beerclient.config;

import dev.baliak.beerclient.client.BeerClientApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Spring configuration for the declarative {@link BeerClientApi} HTTP client.
 *
 * <p>{@code @ImportHttpServices} instructs Spring to generate a proxy implementation
 * of {@link BeerClientApi} at startup and register it as a bean. The proxy delegates
 * all method calls to an underlying {@code RestClient} configured here.</p>
 *
 * <p>The base URL is injected from the {@code beer-store.base-url} property, which
 * allows different values per environment (local, Docker, cloud) without changing code:
 * <pre>
 * # application-dev.yaml
 * beer-store:
 *   base-url: http://localhost:8080
 *
 * # application-docker.yaml / environment variable
 * BEER_API_BASE_URL: http://beer-store:8080
 * </pre>
 * </p>
 */
@Configuration
@ImportHttpServices(types = {BeerClientApi.class})
class BeerClientConfig {

    /**
     * Configures the {@code RestClient} used by the generated {@link BeerClientApi} proxy.
     *
     * <p>{@code RestClientHttpServiceGroupConfigurer} is the Spring Boot 4.x mechanism
     * for customising the underlying HTTP client of {@code @HttpExchange} proxies.
     * {@code forEachClient} iterates over all registered client groups and applies
     * the builder configuration to each one.</p>
     *
     * @param baseUrl base URL of the remote beer-store API, resolved from
     *                {@code beer-store.base-url} property
     * @return configurer that sets the base URL and default Accept header
     */
    @Bean
    RestClientHttpServiceGroupConfigurer groupConfigurer(@Value("${beer-store.base-url}") String baseUrl) {
        return groups -> groups.forEachClient((_, builder) -> builder
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/json")
                .build());
    }
}
