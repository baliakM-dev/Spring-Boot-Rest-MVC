package com.restmvc.beer_store.repositories;

import com.restmvc.beer_store.entities.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for {@link Category} entity persistence operations.
 *
 * <p>Extends {@link JpaRepository} to provide standard CRUD operations,
 * pagination, and sorting capabilities. Additional query methods enforce
 * business constraints such as uniqueness checks on the description field.</p>
 *
 * <p>This repository acts as the data access abstraction layer and should
 * not contain business logic.</p>
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /**
     * Checks whether a category with the given description already exists
     * (case-insensitive comparison).
     *
     * @param description the category description to check
     * @return {@code true} if a category with the given description exists, otherwise {@code false}
     */
    boolean existsByDescriptionIgnoreCase(String description);

    /**
     * Checks whether a category with the given description already exists,
     * excluding a specific entity by its identifier.
     *
     * <p>Used during update operations to allow a category to keep its own description
     * without triggering a false uniqueness conflict.</p>
     *
     * @param description the category description to check
     * @param excludeId   the UUID of the category to exclude from the check
     * @return {@code true} if another category with the same description exists, otherwise {@code false}
     */
    boolean existsByDescriptionIgnoreCaseAndIdNot(String description, UUID excludeId);

    /**
     * Find all categories whose description contains the given string (case-insensitive).
     *
     * @param description the description substring to search for
     * @param pageable    pagination and sorting parameters
     * @return a page of categories matching the description filter
     */
    Page<Category> findByDescriptionContainingIgnoreCase(String description, Pageable pageable);
}
