package com.restmvc.beer_store;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Beer Store Spring Boot application.
 *
 * <p>This class bootstraps the application context using {@link SpringBootApplication},
 * which enables auto-configuration, component scanning, and property support.</p>
 */
@SpringBootApplication
public class BeerStoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(BeerStoreApplication.class, args);
	}
}
