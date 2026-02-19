package com.restmvc.beer_store.repositories;

import com.restmvc.beer_store.entities.Beer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
     * <p>Used during update operations to allow a beer to keep its own name
     * without triggering a false uniqueness conflict.</p>
     *
     * @param beerName name of the beer to check
     * @param id       identifier of the entity to exclude from the check
     * @return {@code true} if another beer with the same name exists, otherwise {@code false}
     */
    boolean existsByBeerNameIgnoreCaseAndIdNot(String beerName, UUID id);

    /**
     * Find all beers whose name contains the given string (case-insensitive).
     *
     * @param beerName the beer name substring to search for
     * @param pageable pagination and sorting parameters
     * @return a page of beers matching the name filter
     */
    Page<Beer> findAllByBeerNameContainingIgnoreCase(String beerName, Pageable pageable);

    /**
     * Find all beers whose UPC contains the given string (case-insensitive).
     *
     * @param upc      the UPC substring to search for
     * @param pageable pagination and sorting parameters
     * @return a page of beers matching the UPC filter
     */
    Page<Beer> findAllByUpcContainingIgnoreCase(String upc, Pageable pageable);

    /**
     * Find all beers whose name and UPC both contain the given strings (case-insensitive).
     *
     * @param beerName the beer name substring to search for
     * @param upc      the UPC substring to search for
     * @param pageable pagination and sorting parameters
     * @return a page of beers matching both filters
     */
    Page<Beer> findAllByBeerNameContainingIgnoreCaseAndUpcContainingIgnoreCase(String beerName, String upc, Pageable pageable);

    /**
     * Returns a paginated list of all beers without eagerly fetching collections.
     *
     * <p><b>IMPORTANT:</b> Do NOT use {@code @EntityGraph} or fetch join for collections
     * (e.g. categories) on pageable queries. Hibernate cannot apply database-level
     * pagination when a collection fetch (join fetch / entity graph) is combined with
     * {@link Pageable}, which would trigger:
     * <pre>HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory</pre>
     * Instead, LAZY loading combined with {@code hibernate.default_batch_fetch_size} is used
     * to load categories efficiently in a single {@code WHERE beer_id IN (...)} query.</p>
     *
     * @param pageable pagination and sorting parameters
     * @return a page of beers
     */
    Page<Beer> findAll(Pageable pageable);

    /**
     * Loads a {@link Beer} by ID and eagerly fetches its categories using an entity graph.
     *
     * <p>Used for single-entity lookups (GET by ID, update, delete) where all
     * category data is needed immediately to avoid {@code LazyInitializationException}.</p>
     *
     * @param id the UUID of the beer to load
     * @return an {@link Optional} containing the beer with categories, or empty if not found
     */
    @EntityGraph(attributePaths = "categories")
    Optional<Beer> findWithCategoriesById(UUID id);

    /**
     * Find a paginated list of beers that belong to the given category.
     *
     * @param categoryId the UUID of the category to filter by
     * @param pageable   pagination and sorting parameters
     * @return a page of beers associated with the specified category
     */
    @Query("""
            select b
                from Beer b
                join b.categories c
                where c.id = :categoryId
            """)
    Page<Beer> findBeersByCategoryId(UUID categoryId, Pageable pageable);
}
