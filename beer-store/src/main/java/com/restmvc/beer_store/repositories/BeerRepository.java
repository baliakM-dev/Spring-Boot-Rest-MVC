package com.restmvc.beer_store.repositories;

import com.restmvc.beer_store.entities.Beer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for {@link Beer} entity persistence operations.
 *
 * <p>Extends {@link JpaRepository} to provide standard CRUD operations,
 * pagination, and sorting capabilities. Additional query methods are
 * defined for enforcing business constraints such as uniqueness checks.</p>
 *
 * <p>This repository acts as the data access abstraction layer and should
 * not contain business logic.</p>
 */
public interface BeerRepository extends JpaRepository<Beer, UUID> {

    /**
     * Checks whether a beer with the given name already exists
     * (case-insensitive comparison).
     *
     * @param beerName name of the beer to check
     * @return {@code true} if a beer with the given name exists, otherwise {@code false}
     */
    boolean existsByBeerNameIgnoreCase(String beerName);


    /**
     * Checks whether a beer with the given name already exists,
     * excluding a specific entity by its identifier.
     *
     * @param beerName name of the beer to check
     * @param id       identifier of the entity to exclude from the check
     * @return {@code true} if another beer with the same name exists, otherwise {@code false}
     */
    boolean existsByBeerNameIgnoreCaseAndIdNot(String beerName, UUID id);

    /**
     * Find all beers by beer name (case-insensitive).
     *
     * @param beerName the beer name to search for
     * @param pageable pagination parameters
     * @return a page of beers matching the name
     */
    Page<Beer> findAllByBeerNameContainingIgnoreCase(String beerName, Pageable pageable);

    /**
     * Find all beers by upc (case-insensitive).
     *
     * @param upc      the UPC to search for
     * @param pageable pagination parameters
     * @return a page of beers matching the UPC
     */
    Page<Beer> findAllByUpcContainingIgnoreCase(String upc, Pageable pageable);

    /**
     * Find all beers by beer name and upc (case-insensitive).
     *
     * @param beerName the beer name to search for
     * @param upc      the upc to search for
     * @param pageable pagination parameters
     * @return a page of beers matching the name and UPC
     */
    Page<Beer> findAllByBeerNameContainingIgnoreCaseAndUpcContainingIgnoreCase(String beerName, String upc, Pageable pageable);

    /**
     * IMPORTANT:
     * Do NOT use @EntityGraph or fetch join for collections (e.g. categories)
     * on pageable queries.
     * Hibernate cannot apply database-level pagination when a collection fetch
     * (join fetch / entity graph) is used together with Pageable.
     * This would trigger:
     * HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
     * Instead, we rely on LAZY loading + batch fetching (hibernate.default_batch_fetch_size)
     * to efficiently load categories without N+1.
     *
     * @param pageable pagination parameters
     * @return a page of beers
     */
    Page<Beer> findAll(Pageable pageable);

    /**
     * Loads Beer by id and eagerly fetches categories to avoid LazyInitializationException
     *
     * @param id beer id
     * @return a beer
     */
    @EntityGraph(attributePaths = "categories")
    Optional<Beer> findWithCategoriesById(UUID id);

}


