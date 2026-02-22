package com.restmvc.beer_store.entities;

import com.restmvc.beer_store.enums.AddressType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a physical address belonging to a {@link Customer}.
 *
 * <p>A customer can have multiple addresses of different types:
 * <ul>
 *   <li>{@link AddressType#PERMANENT} — trvalé bydlisko, used as fallback for tax country.</li>
 *   <li>{@link AddressType#SHIPPING} — doručovacia adresa, primary source for tax country at checkout.</li>
 * </ul>
 * </p>
 *
 * <p>Soft delete pattern: addresses are never physically deleted ({@code isActive = false} instead).
 * This preserves historical shipment references — {@link BeerOrderShipment} holds a nullable FK
 * to this entity, and deactivated addresses must remain readable for order history.</p>
 *
 * <p>Tax country resolution at checkout:
 * <ol>
 *   <li>Use the explicitly selected shipping address country.</li>
 *   <li>Fallback: first SHIPPING address of the customer.</li>
 *   <li>Fallback: first PERMANENT address of the customer.</li>
 *   <li>No address → throw {@code NoAddressException} (400).</li>
 * </ol>
 * </p>
 *
 * <p>Relationships:
 * <ul>
 *   <li>ManyToOne → Customer: address belongs to exactly one customer (FK customer_id NOT NULL).</li>
 *   <li>Referenced by BeerOrderShipment via nullable FK — no JPA back-reference needed here.</li>
 * </ul>
 * </p>
 */
@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "customer_addresses",
        indexes = {
                // Speeds up "all addresses for a customer" queries — used on every checkout.
                @Index(name = "idx_addr_customer_id", columnList = "customer_id"),
                // Speeds up tax lookup by country — used when resolving TaxRate at checkout.
                @Index(name = "idx_addr_country", columnList = "country")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class Address {

    @Id
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "address_id", columnDefinition = "VARCHAR(36)", updatable = false, nullable = false)
    private UUID id;

    @Version
    @Column(name = "version")
    private Long version;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Owning side of the ManyToOne — FK customer_id is on this table.
    // optional = false: an address must always belong to a customer.
    // LAZY: when loading an address, we rarely need the full customer graph.
    // No cascade: customer lifecycle is managed independently of addresses.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    // PERMANENT = trvalé bydlisko (tax fallback)
    // SHIPPING  = doručovacia adresa (primary tax source)
    // DB CHECK constraint (chk_address_type) provides additional guard.
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AddressType type;

    @Column(name = "street", nullable = false, length = 150)
    private String street;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "zip", nullable = false, length = 20)
    private String zip;

    // ISO 3166-1 alpha-2 country code (e.g. "SK", "CZ", "DE").
    // Used to resolve the correct TaxRate at checkout.
    // CHAR(2) — fixed length, always exactly 2 characters.
    @Column(name = "country", nullable = false, length = 2)
    private String country;

    // Soft delete flag — addresses are NEVER physically deleted.
    // BeerOrderShipment holds a FK to this table; hard deletes would break shipment history.
    // Service layer sets isActive = false instead of calling repository.delete().
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Address other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
