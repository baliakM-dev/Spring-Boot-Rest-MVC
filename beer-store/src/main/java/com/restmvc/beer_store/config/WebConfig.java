package com.restmvc.beer_store.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

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
public class WebConfig {
}
