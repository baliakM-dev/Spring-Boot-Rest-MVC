package com.restmvc.beer_store.dtos;

import com.opencsv.bean.CsvBindByName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BeerCsvRecord {
    @NotBlank
    @CsvBindByName(column = "beerName", required = true)
    private String beerName;

    @CsvBindByName(column = "upc", required = true)
    private String upc;

    @CsvBindByName(column = "quantityOnHand")
    private Integer quantityOnHand;

    @CsvBindByName(column = "price", required = true)
    private BigDecimal price;

    @CsvBindByName(column = "categories")
    private String categories; // "IPA;Pale Ale"
}
