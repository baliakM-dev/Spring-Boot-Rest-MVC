package com.restmvc.beer_store.repositories;

import com.restmvc.beer_store.entities.Beer;
import org.springframework.data.jpa.repository.JpaRepository;

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
     * <p>Primarily used to enforce uniqueness validation
     * before creating a new entity.</p>
     *
     * @param beerName name of the beer to check
     * @return {@code true} if a beer with the given name exists, otherwise {@code false}
     */
    boolean existsByBeerNameIgnoreCase(String beerName);


    /**
     * Checks whether a beer with the given name already exists,
     * excluding a specific entity by its identifier.
     *
     * <p>Typically used during update operations to validate uniqueness
     * while ignoring the current entity.</p>
     *
     * @param beerName name of the beer to check
     * @param id       identifier of the entity to exclude from the check
     * @return {@code true} if another beer with the same name exists, otherwise {@code false}
     */
    boolean existsByBeerNameIgnoreCaseAndIdNot(String beerName, UUID id);
}
