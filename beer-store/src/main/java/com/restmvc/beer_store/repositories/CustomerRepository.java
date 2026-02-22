package com.restmvc.beer_store.repositories;

import com.restmvc.beer_store.entities.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Customer} entities.
 *
 * <p>Extends {@link JpaRepository} to inherit standard CRUD operations
 * ({@code save}, {@code findById}, {@code findAll}, {@code delete}, etc.)
 * plus pagination and sorting support.</p>
 *
 * <p>All derived query methods use Spring Data's query derivation — no JPQL or native SQL
 * is needed. Spring Data generates the implementation at startup from the method names.</p>
 *
 * <p>Uniqueness check methods ({@code existsBy...}) are used by the service layer to produce
 * user-friendly 409 Conflict errors before a DB-level UNIQUE constraint violation occurs.
 * The {@code ...AndIdNot} variants exclude the current customer from the check during updates,
 * preventing false conflicts when a customer saves without changing their name or email.</p>
 */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    // -------------------------------------------------------------------------
    // Uniqueness checks for CREATE (no exclusion)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if any customer has the given name (case-insensitive).
     * Used to prevent duplicate names before inserting a new customer.
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Returns {@code true} if any customer has the given email (case-insensitive).
     * Used to prevent duplicate emails before inserting a new customer.
     */
    boolean existsByEmailIgnoreCase(String email);

    // -------------------------------------------------------------------------
    // Uniqueness checks for UPDATE (exclude the customer being updated)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if any customer OTHER than {@code excludeId} has the given name
     * (case-insensitive). Used during updates to avoid a false conflict with the customer's
     * own existing name.
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID excludeId);

    /**
     * Returns {@code true} if any customer OTHER than {@code excludeId} has the given email
     * (case-insensitive). Used during updates to avoid a false conflict with the customer's
     * own existing email.
     */
    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID excludeId);

    // -------------------------------------------------------------------------
    // Filtered paginated queries (used by getAllCustomers)
    // -------------------------------------------------------------------------

    /**
     * Returns a page of customers whose email contains the given substring (case-insensitive).
     * Backed by a DB LIKE '%email%' query.
     */
    Page<Customer> findByEmailContainingIgnoreCase(String email, Pageable pageable);

    /**
     * Returns a page of customers whose name contains the given substring (case-insensitive).
     * Backed by a DB LIKE '%name%' query.
     */
    Page<Customer> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Returns a page of customers whose name AND email both contain the given substrings
     * (case-insensitive, AND condition). Used when both filters are provided simultaneously.
     */
    Page<Customer> findByNameContainingIgnoreCaseAndEmailContainingIgnoreCase(
            String name, String email, Pageable pageable);
}
