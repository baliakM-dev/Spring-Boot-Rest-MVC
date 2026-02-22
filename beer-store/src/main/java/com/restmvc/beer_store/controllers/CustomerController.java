package com.restmvc.beer_store.controllers;

import com.restmvc.beer_store.dtos.customer.CustomerCreateRequestDTO;
import com.restmvc.beer_store.dtos.customer.CustomerResponseDTO;
import com.restmvc.beer_store.services.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller for managing customers.
 *
 * <p>Exposes CRUD endpoints under {@value BASE_URL}.
 * All business logic is delegated to {@link CustomerService} — the controller
 * is intentionally thin (no business logic, no direct repository access).</p>
 *
 * <p>HTTP status conventions:
 * <ul>
 *   <li>POST → 201 Created (Location header points to the new resource)</li>
 *   <li>GET → 200 OK</li>
 *   <li>PUT → 200 OK (returns updated representation)</li>
 *   <li>DELETE → 204 No Content</li>
 * </ul>
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public class CustomerController {

    /**
     * Base path for all customer endpoints.
     */
    public static final String BASE_URL = "/api/v1/customers";

    /**
     * Base path for single-customer endpoints; includes the path variable.
     */
    public static final String BASE_URL_ID = BASE_URL + "/{customerId}";

    // Only the service is injected — direct repository access belongs in the service layer (SRP).
    private final CustomerService customerService;

    /**
     * Creates a new customer.
     *
     * @param customerCreateRequestDTO validated request body with customer data
     * @return 201 Created with a Location header pointing to the new customer resource
     */
    @PostMapping(BASE_URL)
    public ResponseEntity<Void> createCustomer(@Valid @RequestBody CustomerCreateRequestDTO customerCreateRequestDTO) {
        log.info("Creating customer: {}", customerCreateRequestDTO);
        UUID savedId = customerService.createCustomer(customerCreateRequestDTO);
        return ResponseEntity.created(URI.create(BASE_URL + "/" + savedId)).build();
    }

    /**
     * Returns a paginated list of customers, optionally filtered by name and/or email.
     *
     * @param name     optional substring filter on customer name (case-insensitive)
     * @param email    optional substring filter on customer email (case-insensitive)
     * @param pageable pagination and sorting parameters; defaults to name ASC
     * @return 200 OK with a page of {@link CustomerResponseDTO}
     */
    @GetMapping(BASE_URL)
    public ResponseEntity<Page<CustomerResponseDTO>> getAllCustomers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        log.info("Retrieving all customers: name={}, email={}, pageable={}", name, email, pageable);
        return ResponseEntity.ok(customerService.getAllCustomers(name, email, pageable));
    }

    /**
     * Returns a single customer by their UUID.
     *
     * @param customerId the customer's UUID
     * @return 200 OK with the customer data, or 404 if not found
     */
    @GetMapping(BASE_URL_ID)
    public ResponseEntity<CustomerResponseDTO> getCustomerById(@PathVariable UUID customerId) {
        log.info("Retrieving customer by ID: {}", customerId);
        return ResponseEntity.ok(customerService.getCustomerById(customerId));
    }

    /**
     * Replaces a customer's profile with the provided data (full update).
     *
     * @param customerId               the UUID of the customer to update
     * @param customerCreateRequestDTO validated request body with the new customer data
     * @return 200 OK with the updated customer representation, or 404 if not found
     */
    @PutMapping(BASE_URL_ID)
    public ResponseEntity<CustomerResponseDTO> updateCustomerById(
            @PathVariable UUID customerId,
            @Valid @RequestBody CustomerCreateRequestDTO customerCreateRequestDTO
    ) {
        log.info("Updating customer by ID: {}", customerId);
        return ResponseEntity.ok(customerService.updateCustomerById(customerId, customerCreateRequestDTO));
    }

    /**
     * Deletes a customer by their UUID.
     *
     * @param customerId the UUID of the customer to delete
     * @return 204 No Content on success, or 404 if not found
     */
    @DeleteMapping(BASE_URL_ID)
    public ResponseEntity<Void> deleteCustomerById(@PathVariable UUID customerId) {
        log.info("Deleting customer by ID: {}", customerId);
        customerService.deleteCustomerById(customerId);
        return ResponseEntity.noContent().build();
    }
}
