package com.restmvc.beer_store.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import jakarta.annotation.PostConstruct;

/**
 * Configuration for JPA Auditing.
 *
 * <p>Enables automatic population of {@code @CreatedDate} and {@code @LastModifiedDate}
 * fields on entities annotated with {@code @EntityListeners(AuditingEntityListener.class)}.
 * This eliminates the need for manual timestamp management in the service layer.</p>
 */
@Slf4j
@Configuration
@EnableJpaAuditing
public class JpaConfig {

    @PostConstruct
    void init() {
        log.debug("JpaConfig loaded – JPA auditing enabled");
    }
}
