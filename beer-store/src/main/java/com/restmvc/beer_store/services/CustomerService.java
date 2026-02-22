package com.restmvc.beer_store.services;

import com.restmvc.beer_store.dtos.customer.CustomerCreateRequestDTO;
import com.restmvc.beer_store.dtos.customer.CustomerResponseDTO;
import com.restmvc.beer_store.entities.Customer;
import com.restmvc.beer_store.exceptions.ResourceAlreadyExistsExceptions;
import com.restmvc.beer_store.exceptions.ResourceNotFoundException;
import com.restmvc.beer_store.mappers.CustomerMapper;
import com.restmvc.beer_store.repositories.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Service layer for customer management operations.
 *
 * <p>Handles all business logic related to customers: creation, retrieval, update,
 * and deletion. Uniqueness constraints on name and email are enforced here (in addition
 * to DB-level UNIQUE constraints) to provide user-friendly error messages before hitting
 * the database.</p>
 *
 * <p>Transaction strategy:
 * <ul>
 *   <li>Read-only methods use {@code @Transactional(readOnly = true)} — Hibernate skips
 *       dirty checking for read-only transactions, improving performance.</li>
 *   <li>Write methods use {@code @Transactional} (read-write) to ensure atomicity.</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    /**
     * Creates a new customer after validating that the name and email are not already taken.
     *
     * @param customerCreateRequestDTO validated DTO with the new customer's data
     * @return the UUID of the newly created customer (used by the controller to build the Location header)
     * @throws ResourceAlreadyExistsExceptions if a customer with the same name or email already exists
     */
    @Transactional
    public UUID createCustomer(CustomerCreateRequestDTO customerCreateRequestDTO) {
        log.info("Creating customer: {}", customerCreateRequestDTO);

        // 1) Validate uniqueness before hitting the DB with a duplicate key violation.
        validateUniqueNameAndEmail(
                customerCreateRequestDTO.name(),
                customerCreateRequestDTO.email(),
                null);

        // 2) Map DTO to entity (audit fields and ID are managed by JPA/Hibernate).
        Customer customer = customerMapper.dtoToCustomer(customerCreateRequestDTO);

        // 3) Persist and return the generated UUID for use in the Location response header.
        return customerRepository.save(customer).getId();
    }

    /**
     * Returns a paginated and optionally filtered list of customers.
     *
     * <p>Supported filter combinations:
     * <ul>
     *   <li>name + email → both filters applied (AND)</li>
     *   <li>name only → filtered by name substring</li>
     *   <li>email only → filtered by email substring</li>
     *   <li>none → all customers returned</li>
     * </ul>
     * All string comparisons are case-insensitive.</p>
     *
     * @param name     optional name substring filter
     * @param email    optional email substring filter
     * @param pageable pagination and sorting parameters
     * @return a page of {@link CustomerResponseDTO}
     */
    @Transactional(readOnly = true)
    public Page<CustomerResponseDTO> getAllCustomers(
            String name,
            String email,
            Pageable pageable
    ) {
        log.info("Retrieving all customers: name={}, email={}, pageable={}", name, email, pageable);

        boolean hasName = StringUtils.hasText(name);
        boolean hasEmail = StringUtils.hasText(email);

        Page<Customer> customers;
        if (hasName && hasEmail) {
            customers = customerRepository
                    .findByNameContainingIgnoreCaseAndEmailContainingIgnoreCase(name, email, pageable);
        } else if (hasName) {
            customers = customerRepository.findByNameContainingIgnoreCase(name, pageable);
        } else if (hasEmail) {
            customers = customerRepository.findByEmailContainingIgnoreCase(email, pageable);
        } else {
            customers = customerRepository.findAll(pageable);
        }

        return customers.map(customerMapper::entityToResponse);
    }

    /**
     * Returns a single customer by UUID.
     *
     * @param customerId the customer's UUID
     * @return the customer as a {@link CustomerResponseDTO}
     * @throws ResourceNotFoundException if no customer with the given UUID exists
     */
    @Transactional(readOnly = true)
    public CustomerResponseDTO getCustomerById(UUID customerId) {
        log.info("Retrieving customer by ID: {}", customerId);
        return customerMapper.entityToResponse(getCustomerOrThrow(customerId));
    }

    /**
     * Fully replaces a customer's profile data.
     *
     * <p>Uniqueness validation is skipped if neither name nor email changed — this avoids
     * a false conflict when the customer is saved without modifying those fields.</p>
     *
     * @param customerId               the UUID of the customer to update
     * @param customerCreateRequestDTO validated DTO with the new customer data
     * @return the updated customer as a {@link CustomerResponseDTO}
     * @throws ResourceNotFoundException       if no customer with the given UUID exists
     * @throws ResourceAlreadyExistsExceptions if the new name or email conflicts with another customer
     */
    @Transactional
    public CustomerResponseDTO updateCustomerById(UUID customerId, CustomerCreateRequestDTO customerCreateRequestDTO) {
        log.info("Updating customer by ID: {}", customerId);

        // 1) Load existing customer or throw 404.
        Customer customer = getCustomerOrThrow(customerId);

        // 2) Only validate uniqueness if name or email actually changed.
        //    Skipping when unchanged avoids a false conflict against the customer's own record.
        boolean nameChanged = !customer.getName().equalsIgnoreCase(customerCreateRequestDTO.name());
        boolean emailChanged = !customer.getEmail().equalsIgnoreCase(customerCreateRequestDTO.email());

        if (nameChanged || emailChanged) {
            validateUniqueNameAndEmail(
                    customerCreateRequestDTO.name(),
                    customerCreateRequestDTO.email(),
                    customerId   // exclude the current customer from the uniqueness check
            );
        }

        // 3) Apply changes from DTO to the managed entity and return the updated representation.
        customerMapper.updateCustomerFromDto(customerCreateRequestDTO, customer);
        return customerMapper.entityToResponse(customerRepository.save(customer));
    }

    /**
     * Deletes a customer by UUID.
     *
     * <p>Note: if the customer has existing orders the database FK constraint
     * ({@code NOT NULL, no ON DELETE CASCADE}) will prevent deletion. The service
     * layer should guard against this case before reaching the repository.</p>
     *
     * @param customerId the UUID of the customer to delete
     * @throws ResourceNotFoundException if no customer with the given UUID exists
     */
    @Transactional
    public void deleteCustomerById(UUID customerId) {
        log.info("Deleting customer by ID: {}", customerId);
        // Load first to produce a 404 if the customer does not exist,
        // then delete the managed entity directly (avoids a second SELECT by ID).
        Customer customer = getCustomerOrThrow(customerId);
        customerRepository.delete(customer);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates that neither the given name nor email is already used by another customer.
     *
     * <p>When {@code excludeId} is provided, the customer with that UUID is excluded from
     * the duplicate check — used during updates so a customer can keep their own name/email.</p>
     *
     * @param customerName  the name to check (case-insensitive)
     * @param customerEmail the email to check (case-insensitive)
     * @param excludeId     UUID to exclude from the check, or {@code null} for create operations
     * @throws ResourceAlreadyExistsExceptions if name or email is already taken
     */
    private void validateUniqueNameAndEmail(String customerName, String customerEmail, UUID excludeId) {
        boolean nameExists = excludeId == null
                ? customerRepository.existsByNameIgnoreCase(customerName)
                : customerRepository.existsByNameIgnoreCaseAndIdNot(customerName, excludeId);

        boolean emailExists = excludeId == null
                ? customerRepository.existsByEmailIgnoreCase(customerEmail)
                : customerRepository.existsByEmailIgnoreCaseAndIdNot(customerEmail, excludeId);

        if (nameExists || emailExists) {
            throw new ResourceAlreadyExistsExceptions(
                    "Customer",
                    "name", customerName,
                    "email", customerEmail
            );
        }
    }

    /**
     * Loads a customer by UUID or throws {@link ResourceNotFoundException}.
     *
     * @param customerId the UUID to look up
     * @return the {@link Customer} entity
     * @throws ResourceNotFoundException if no customer with the given UUID exists
     */
    Customer getCustomerOrThrow(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId.toString()));
    }
}
