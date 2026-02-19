package dev.baliak.beerclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class BeerClientHttpexchangeApplication {

	public static void main(String[] args) {
		SpringApplication.run(BeerClientHttpexchangeApplication.class, args);
	}

}
