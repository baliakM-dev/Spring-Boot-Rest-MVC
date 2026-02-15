package com.restmvc.beer_store.dtos.beer;

import com.opencsv.bean.CsvBindByName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Data transfer object for CSV records.
 * Include all fields from the CSV file.
 * Example:
 * {
 *   "beerName": "IPA",
 *   "upc": "123456789012",
 *   "quantityOnHand": 100,
 *   "price": 5.99,
 *   "categories": "IPA;Pale Ale"
 * }
 */
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
