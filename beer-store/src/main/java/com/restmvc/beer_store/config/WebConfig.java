package com.restmvc.beer_store.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * Web configuration for Spring Data web support.
 *
 * <p>Enables Spring Data web integration including:</p>
 * <ul>
 *     <li>Automatic binding of {@link org.springframework.data.domain.Pageable} and
 *         {@link org.springframework.data.domain.Sort} parameters in controller methods</li>
 *     <li>Stable JSON serialization of {@link org.springframework.data.domain.Page} responses
 *         using DTO-based structure ({@code VIA_DTO})</li>
 * </ul>
 *
 * <p>The {@code VIA_DTO} serialization mode ensures predictable REST API responses
 * and avoids exposing internal {@code Page} implementation details.</p>
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig implements WebMvcConfigurer {

    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        PageableHandlerMethodArgumentResolver pageableResolver =
                new PageableHandlerMethodArgumentResolver();

        pageableResolver.setMaxPageSize(MAX_PAGE_SIZE);

        // To start page numbering from 1 instead of 0, uncomment the line below:
        // pageableResolver.setOneIndexedParameters(true);

        resolvers.add(pageableResolver);
    }
}
