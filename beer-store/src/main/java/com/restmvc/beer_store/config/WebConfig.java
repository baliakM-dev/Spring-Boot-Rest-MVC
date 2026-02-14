package com.restmvc.beer_store.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * Web configuration for Spring Data support.
 * Enables Spring Data web integration including
 * - automatic binding of Pageable and Sort parameters in controller methods
 * - stable JSON serialization of Page responses using DTO-based structure (VIA_DTO)
 * The VIA_DTO mode ensures predictable REST API responses and avoids
 * exposing internal Page implementation details.
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

        // If wnat to start with page 1 not 0
        // pageableResolver.setOneIndexedParameters(true);

        resolvers.add(pageableResolver);
    }
}
