package com.restmvc.beer_store.dtos.customer;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only projection of a {@link com.restmvc.beer_store.entities.Customer} entity
 * returned by all customer endpoints (list and detail).
 *
 * <p>Design decision — orders are NOT included here:
 * <ul>
 *   <li>Including orders in a paginated list response would trigger an N+1 query problem
 *       (one extra SELECT per customer row).</li>
 *   <li>{@code Customer.orders} uses {@code FetchType.LAZY}; accessing it outside an
 *       active transaction would throw a {@code LazyInitializationException}.</li>
 *   <li>Order history is a separate concern — use the dedicated order endpoints
 *       (e.g. {@code GET /api/v1/orders?customerId=...}) to retrieve a customer's orders.</li>
 * </ul>
 * </p>
 */
public record CustomerResponseDTO(
        /** The customer's UUID primary key. */
        UUID id,

        /** The customer's full name. */
        String name,

        /** The customer's unique email address. */
        String email,

        /** The customer's contact phone number. */
        String phoneNumber,

        /** Timestamp of when this customer record was created (set by JPA Auditing). */
        LocalDateTime createdAt,

        /** Timestamp of the most recent update to this customer record (set by JPA Auditing). */
        LocalDateTime updatedAt
) {
}
