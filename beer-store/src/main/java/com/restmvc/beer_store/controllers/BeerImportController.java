package com.restmvc.beer_store.controllers;

import com.restmvc.beer_store.services.BeerImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for bulk beer import operations.
 * Endpoint: POST /api/v1/import/beers
 * Accepts: multipart/form-data with CSV file
 * CSV Format:
 * beerName, upc,quantityOnHand, price,categories
 */
@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
@Slf4j
public class BeerImportController {

    private final BeerImportService beerImportService;

    /**
     * Import beers from a CSV file.
     * Request:
     * - Content-Type: multipart/form-data
     * - Part name: "file"
     * - File type: CSV
     *
     * @param file CSV file with beer data
     * @return Import statistics
     */
    @PostMapping(value = "/beers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> importBeers(@RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(beerImportService.importCsv(file));
    }
}