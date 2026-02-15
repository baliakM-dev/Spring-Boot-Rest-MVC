package com.restmvc.beer_store.services;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvException;
import com.restmvc.beer_store.dtos.beer.BeerCsvRecord;
import com.restmvc.beer_store.entities.Beer;
import com.restmvc.beer_store.entities.Category;
import com.restmvc.beer_store.repositories.BeerRepository;
import com.restmvc.beer_store.repositories.CategoryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service responsible for importing Beer data from CSV files.
 * Features:
 * - Batch processing to reduce memory footprint
 * - Category ID caching to avoid repeated DB lookups
 * - EntityManager clearing between batches
 * - Basic validation per CSV row
 * - Error aggregation (first 10 errors returned)
 * This implementation is optimized for performance testing (e.g. N+1 analysis).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BeerImportService {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_ERRORS_RETURNED = 10;

    private final BeerRepository beerRepository;
    private final CategoryRepository categoryRepository;
    private final EntityManager em;

    /**
     * Imports beers from a CSV file.
     *
     * @param file CSV file containing beer data
     * @return ImportResult summary with statistics
     */
    @Transactional
    public ImportResult importCsv(MultipartFile file) throws Exception {

        long start = System.currentTimeMillis();

        // Cache of category description (lowercase) -> category UUID
        Map<String, UUID> categoryIdCache = preloadCategoryIds();

        long imported = 0;
        long skipped = 0;
        int createdCategories = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            CsvToBean<BeerCsvRecord> csv = new CsvToBeanBuilder<BeerCsvRecord>(reader)
                    .withType(BeerCsvRecord.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withThrowExceptions(false) // Capture parse errors instead of failing the whole import
                    .build();

            // Parse all records first (this also populates capturedExceptions)
            List<BeerCsvRecord> records = csv.parse();

            // Convert OpenCSV parse errors into ImportResult (instead of noisy logs)
            skipped += addCapturedCsvErrors(csv.getCapturedExceptions(), errors);

            List<Beer> batch = new ArrayList<>(BATCH_SIZE);
            int lineNumber = 1; // header row, data starts at line 2

            for (BeerCsvRecord record : records) {
                lineNumber++;

                try {
                    validateRecord(record);

                    Beer beer = Beer.builder()
                            .beerName(record.getBeerName().trim())
                            .upc(record.getUpc().trim())
                            .quantityOnHand(record.getQuantityOnHand())
                            .price(record.getPrice())
                            .build();

                    createdCategories += attachCategories(record, beer, categoryIdCache);

                    batch.add(beer);

                    if (batch.size() == BATCH_SIZE) {
                        persistBatch(batch);
                        imported += batch.size();
                        batch.clear();
                    }

                } catch (Exception e) {
                    skipped++;
                    addError(errors, "Line " + lineNumber + ": " + e.getMessage());
                }
            }

            if (!batch.isEmpty()) {
                persistBatch(batch);
                imported += batch.size();
            }
        }

        long duration = System.currentTimeMillis() - start;

        return new ImportResult(
                imported,
                createdCategories,
                skipped,
                duration,
                errors.isEmpty() ? null : errors
        );
    }

    /**
     * Validates required CSV fields (lightweight validation).
     */
    private void validateRecord(BeerCsvRecord record) {
        if (record.getBeerName() == null || record.getBeerName().isBlank()) {
            throw new IllegalArgumentException("Missing beerName");
        }
        if (record.getUpc() == null || record.getUpc().isBlank()) {
            throw new IllegalArgumentException("Missing upc");
        }
        if (record.getPrice() == null || record.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid price");
        }
    }

    /**
     * Attaches categories to the Beer entity.
     * Creates new categories if they do not exist.
     * Uses EntityManager#getReference to avoid unnecessary SELECT queries.
     *
     * @return number of newly created categories
     */
    private int attachCategories(
            BeerCsvRecord record,
            Beer beer,
            Map<String, UUID> categoryIdCache
    ) {
        if (record.getCategories() == null || record.getCategories().isBlank()) {
            return 0;
        }

        int created = 0;

        for (String raw : record.getCategories().split(";")) {
            String description = raw.trim();
            if (description.isEmpty()) continue;

            String key = description.toLowerCase();
            UUID categoryId = categoryIdCache.get(key);

            if (categoryId == null) {
                Category saved = categoryRepository.save(
                        Category.builder().description(description).build()
                );
                categoryId = saved.getId();
                categoryIdCache.put(key, categoryId);
                created++;
            }

            Category reference = em.getReference(Category.class, categoryId);
            beer.addCategory(reference);
        }

        return created;
    }

    /**
     * Persists a batch of Beer entities and clears the persistence context.
     */
    private void persistBatch(List<Beer> batch) {
        beerRepository.saveAll(batch);
        beerRepository.flush();
        em.clear(); // Prevent memory growth during large imports
    }

    /**
     * Loads existing categories into memory (description -> UUID).
     */
    private Map<String, UUID> preloadCategoryIds() {
        Map<String, UUID> map = new HashMap<>();
        for (Category c : categoryRepository.findAll()) {
            map.put(c.getDescription().toLowerCase(), c.getId());
        }
        return map;
    }

    /**
     * Adds captured OpenCSV errors to the response.
     *
     * @return number of captured errors
     */
    private long addCapturedCsvErrors(List<CsvException> captured, List<String> errors) {
        if (captured == null || captured.isEmpty()) {
            return 0;
        }

        for (CsvException e : captured) {
            // CsvException often contains line number; message is usually enough for debugging
            addError(errors, "CSV parse error: " + e.getMessage());
        }
        return captured.size();
    }

    /**
     * Adds an error message, keeping only the first MAX_ERRORS_RETURNED entries.
     */
    private void addError(List<String> errors, String message) {
        if (errors.size() < MAX_ERRORS_RETURNED) {
            errors.add(message);
        }
    }

    /**
     * Import summary DTO.
     */
    public record ImportResult(
            long imported,
            int categoriesCreated,
            long skippedRows,
            long durationMs,
            List<String> errors
    ) {}
}