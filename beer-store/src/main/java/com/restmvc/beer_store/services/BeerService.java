package com.restmvc.beer_store.services;

import com.restmvc.beer_store.dtos.beer.BeerCreateRequestDTO;
import com.restmvc.beer_store.dtos.beer.BeerResponseDTO;
import com.restmvc.beer_store.dtos.beer.BeerUpdateRequestDTO;
import com.restmvc.beer_store.entities.Beer;
import com.restmvc.beer_store.entities.Category;
import com.restmvc.beer_store.exceptions.ResourceAlreadyExistsExceptions;
import com.restmvc.beer_store.exceptions.ResourceNotFoundException;
import com.restmvc.beer_store.mappers.BeerMapper;
import com.restmvc.beer_store.repositories.BeerRepository;
import com.restmvc.beer_store.repositories.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
     * <p>
     * The method is transactional to ensure atomic persistence.
     * If any exception occurs during the process, the transaction
     * will be rolled back automatically.
     * </p>
     *
     * @param beerCreateRequestDTO data required to create a new beer
     * @return the unique identifier ({@link UUID}) of the newly created beer
     * @throws IllegalArgumentException if input data is invalid
     */
    @Transactional
    public UUID createBeer(BeerCreateRequestDTO beerCreateRequestDTO) {
        log.info("Creating beer: {}", beerCreateRequestDTO);
        // 1) validation
        validateUniqueBeerName(beerCreateRequestDTO.beerName(), null);
        // 2) convert dto to entity
        Beer beer = beerMapper.dtoToBeer(beerCreateRequestDTO);
        // 3) associates existing categories with a beer if category IDs are provided.
        associateCategoriesIfProvided(beer, beerCreateRequestDTO.categoryIds());
        // 4) save bees
        Beer savedBeer = beerRepository.save(beer);
        log.info("Successfully created beer: id={}, name={}", savedBeer.getId(), savedBeer.getBeerName());
        return savedBeer.getId();
    }

    /**
     * Retrieve a paginated list of beers (including categories) and optionally filter by name or UPC.
     *
     * <p>
     * This method retrieves a paginated list of beers from the database, optionally filtering by beer name and UPC.
     * It also allows for showing or hiding the inventory on hand for each beer.
     * Returned data are with like operator inside an SQL query
     * </p>
     *
     * @param beerName            name of the beer to filter by (optional)
     * @param upc                 UPC barcode of the beer to filter by (optional)
     * @param showInventoryOnHand quantity on hand flag (optional)
     * @param pageable            pagination parameters
     * @return ({@link BeerResponseDTO})
     */
    @Transactional(readOnly = true)
    public Page<BeerResponseDTO> getAllBeers(String beerName, String upc, Boolean showInventoryOnHand, Pageable pageable) {
        log.info("Retrieving beers: beerName={}, upc={}, showInventoryOnHand={}", beerName, upc, showInventoryOnHand);

        // 1) Initial beerPage and StringUtils for filtering
        Page<Beer> beerPage;
        boolean hasName = StringUtils.hasText(beerName);
        boolean hasUpc = StringUtils.hasText(upc);
        boolean includeInventory = Boolean.TRUE.equals(showInventoryOnHand);

        // 2) Filter beers by name and upc if it doesn't exist return all beers
        if (hasName && hasUpc) {
            beerPage = beerRepository
                    .findAllByBeerNameContainingIgnoreCaseAndUpcContainingIgnoreCase(beerName, upc, pageable);
        } else if (hasName) {
            beerPage = beerRepository.findAllByBeerNameContainingIgnoreCase(beerName, pageable);
        } else if (hasUpc) {
            beerPage = beerRepository.findAllByUpcContainingIgnoreCase(upc, pageable);
        } else {
            beerPage = beerRepository.findAll(pageable);
        }
        // 3) Check if showInventoryOnHand is false, then quantityOnHand will be null
        if (!includeInventory) {
            beerPage.forEach(b -> b.setQuantityOnHand(null));
        }

        log.info("Successfully retrieved beers");
        return beerPage.map(beerMapper::beerToResponseDto);
    }

    /**
     * Retrieve a beer by ID.
     *
     * <p>
     * Retrieves a beer by its unique identifier, including eagerly fetched categories.
     * Throws a ResourceNotFoundException if the beer is not found.
     * </p>
     *
     * @param beerId beer id to retrieve
     * @return {@link BeerResponseDTO} beer
     */
    @Transactional(readOnly = true)
    public BeerResponseDTO getBeerById(UUID beerId) {
        log.info("Retrieving beer by ID: {}", beerId);
        return beerMapper.beerToResponseDto(getBeerOrThrow(beerId));
    }

    /**
     * Updates an existing beer with full replacement.
     * Validation name uniqueness only if name is beeing changed.
     * JPA auditing automatically updates the updatedAt timestamp.
     *
     * @param beerId               the beer id to update
     * @param beerUpdateRequestDTO the data to update the beer with
     * @return updated beer
     * @throws ResourceNotFoundException if beer is not found
     */
    @Transactional
    public BeerResponseDTO updateBeerById(UUID beerId, BeerUpdateRequestDTO beerUpdateRequestDTO) {
        log.info("Updating beer with ID: {}", beerId);

        // 1) Get beer or throw exception if not found
        Beer beer = getBeerOrThrow(beerId);

        // 2) Validate name uniqueness if name is beeing changed
        if (!beer.getBeerName().equalsIgnoreCase(beerUpdateRequestDTO.beerName())) {
            validateUniqueBeerName(beerUpdateRequestDTO.beerName(), beerId);
        }

        // 3) Update beer with new values
        beerMapper.updateBeerFromDto(beerUpdateRequestDTO, beer);
        return beerMapper.beerToResponseDto(beerRepository.saveAndFlush(beer));
    }

//                "createdAt": "2026-02-13T20:34:32.198494",
//                        "updatedAt": "2026-02-13T20:34:32.198494"
    // ============================= Helper Methods =================================

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

            throw new ResourceNotFoundException(
                    "Category",
                    "id",
                    missingIds.toString());
        }
        categories.forEach(beer::addCategory);
    }

    /**
     * Check if beer exists and return it or throw an exception.
     *
     * @param beerId beer id
     * @return Beer or throw exception {@link ResourceNotFoundException}
     */
    private Beer getBeerOrThrow(UUID beerId) {
        return beerRepository.findWithCategoriesById(beerId)
                .orElseThrow(() -> new ResourceNotFoundException("Beer", "id", beerId.toString()));
    }
}