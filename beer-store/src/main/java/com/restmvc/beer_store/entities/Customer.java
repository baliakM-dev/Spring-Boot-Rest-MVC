package com.restmvc.beer_store.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a customer who can place beer orders.
 *
 * Relationships:
 * - OneToMany → BeerOrder: a customer can have multiple orders over time.
 *   mappedBy = "customer": BeerOrder owns the FK column (customer_id) — Customer is the inverse side.
 *   No CascadeType: customer and order lifecycles are intentionally independent.
 *     - Deleting a Customer with existing orders is NOT allowed — the DB FK constraint
 *       (NOT NULL, no ON DELETE CASCADE) will throw a constraint violation.
 *     - The service layer must explicitly prevent customer deletion if orders exist.
 *   LAZY fetch: customer's order list is large and rarely needed in full; load on demand.
 *   This collection is mostly used for reporting or admin views, not for day-to-day order processing.
 *
 * Why no cascade on orders?
 *   Orders are business records — they must be retained for auditing, invoicing, and history
 *   even if a customer account is deactivated. Cascading deletes would silently destroy order history.
 *   Explicit service-layer control is safer and clearer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "customers",
        indexes = {
                // Supports time-range queries (e.g. "customers registered this month").
                @Index(name = "idx_customers_created_at", columnList = "created_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class Customer {

    // UUID primary key stored as VARCHAR(36).
    // @JdbcTypeCode(SqlTypes.VARCHAR) tells Hibernate to use VARCHAR JDBC type for UUID mapping.
    @Id
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "customer_id", columnDefinition = "VARCHAR(36)", updatable = false, nullable = false)
    private UUID id;

    // Optimistic locking — prevents concurrent modification of the same customer record.
    @Version
    @Column(name = "version")
    private Long version;

    // Managed automatically by Spring Data JPA Auditing.
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    @NotBlank
    @Size(max = 100)
    @Column(name = "email", length = 100, nullable = false, unique = true)
    @Email
    private String email;

    @NotBlank
    @Size(max = 20)
    @Column(name = "phone_number", length = 20, nullable = false)
    private String phoneNumber;

    // Inverse (non-owning) side of the bidirectional OneToMany with BeerOrder.
    // BeerOrder holds the FK column — this collection is a view, not the owner.
    // No cascade: deleting a customer does NOT cascade to orders (see class-level Javadoc).
    // LAZY: loading all orders for a customer is expensive — only fetch when explicitly needed.
    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    private Set<BeerOrder> orders = new HashSet<>();

    // Equality based on business identity (PK), not object reference.
    // Safe for use in JPA collections and after detach/proxy unwrapping.
    // Null-safe: transient (not-yet-persisted) entities are never equal to each other.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Customer other)) return false;
        return id != null && id.equals(other.id);
    }

    // Fixed hashCode using class — consistent before and after persist (id changes from null to UUID).
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
