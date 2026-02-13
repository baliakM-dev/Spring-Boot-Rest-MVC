package com.restmvc.beer_store;

import org.springframework.boot.SpringApplication;

public class TestBeerStoreApplication {

	public static void main(String[] args) {
		SpringApplication.from(BeerStoreApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
