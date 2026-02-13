package com.restmvc.beer_store.services;

import com.restmvc.beer_store.dtos.BeerCreateDTO;
import com.restmvc.beer_store.entities.Beer;
import com.restmvc.beer_store.entities.Category;
import com.restmvc.beer_store.exceptions.NotFoundException;
import com.restmvc.beer_store.exceptions.ResourceAlreadyExistsExceptions;
import com.restmvc.beer_store.mappers.BeerMapper;
import com.restmvc.beer_store.repositories.BeerRepository;
import com.restmvc.beer_store.repositories.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer responsible for business operations related to {@code Beer} entities.
 *
 * <p>This service encapsulates domain logic and coordinates persistence
 * operations via {@link BeerRepository}. It acts as a transactional
 * boundary between controllers and the data access layer.</p>
 *
 * <p>Responsibilities include:</p>
 * <ul>
 *     <li>Creating new beers</li>
 *     <li>Applying business validation rules</li>
 *     <li>Handling transactional consistency</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BeerService {

    private final BeerRepository beerRepository;
    private final BeerMapper beerMapper;
    private final CategoryRepository categoryRepository;

    /**
     * Creates a new {@code Beer} entity based on the provided DTO.
     *
     * <p>The method is transactional to ensure atomic persistence.
     * If any exception occurs during the process, the transaction
     * will be rolled back automatically.</p>
     *
     * @param beerCreateDTO data required to create a new beer
     * @return the unique identifier ({@link UUID}) of the newly created beer
     * @throws IllegalArgumentException if input data is invalid
     */
    @Transactional
    public UUID createBeer(BeerCreateDTO beerCreateDTO) {
        log.info("Creating beer: {}", beerCreateDTO);

        // 1) validation
        validateUniqueBeerName(beerCreateDTO.beerName(), null);
        // 2) convert dto to entity
        Beer beer = beerMapper.dtoToBeer(beerCreateDTO);
        // 3) associates existing categories with a beer if category IDs are provided.
        associateCategoriesIfProvided(beer, beerCreateDTO.categoryIds());
        // 4) save bees
        Beer savedBeer = beerRepository.save(beer);
        log.info("Successfully created beer: id={}, name={}", savedBeer.getId(), savedBeer.getBeerName());
        return savedBeer.getId();
    }

    /**
     * Validates that a beer name is unique.
     * For updates, excludeId allows the current beer to keep its name.
     * Case-insensitive check prevents "Pilsner" and "PILSNER" duplicates.
     *
     * @param beerName  the name to validate
     * @param excludeId the beer ID to exclude from check (null for create operations)
     * @throws ResourceAlreadyExistsExceptions if name exists
     */
    private void validateUniqueBeerName(String beerName, UUID excludeId) {
        boolean exists = excludeId == null
                ? beerRepository.existsByBeerNameIgnoreCase(beerName)
                : beerRepository.existsByBeerNameIgnoreCaseAndIdNot(beerName, excludeId);

        if (exists) {
            log.warn("Attempt to create/update beer with duplicate name: {}", beerName);
            throw new ResourceAlreadyExistsExceptions("Beer", "beerName", beerName);
        }
    }

    /**
     * Associates existing categories with a beer if category IDs are provided.
     * Validates that all provided category IDs exist in the database.
     *
     * @param beer        beer entity to associate categories with
     * @param categoryIds category IDs to associate with the beer
     */
    private void associateCategoriesIfProvided(Beer beer, Set<UUID> categoryIds) {

        if (categoryIds == null || categoryIds.isEmpty()) return;

        List<Category> categories = categoryRepository.findAllById(categoryIds);

        if (categories.size() != categoryIds.size()) {
            Set<UUID> foundIds = categories.stream()
                    .map(Category::getId)
                    .collect(Collectors.toSet());

            Set<UUID> missingIds = new HashSet<>(categoryIds);
            missingIds.removeAll(foundIds);

            throw new NotFoundException(
                    "Category",
                    "id",
                    missingIds.toString()
            );
        }

        categories.forEach(beer::addCategory);
    }
}