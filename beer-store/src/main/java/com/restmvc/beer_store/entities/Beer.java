package com.restmvc.beer_store.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Beer entity representing a beer product in the catalog.
 * <p>
 * UNIDIRECTIONAL many-to-many with Category - Beer owns the relationship.
 * This is simpler and cleaner than bidirectional.
 * <p>
 * Uses JPA Auditing for automatic timestamp management.
 */
@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "beers",
        indexes = {
                @Index(name = "idx_beers_upc", columnList = "upc"),
                @Index(name = "idx_beers_created_at", columnList = "created_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class Beer {

    @Id
    @GeneratedValue(generator = "UUID")
    @UuidGenerator
    @Column(name = "beer_id", columnDefinition = "VARCHAR(36)", updatable = false, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Version
    private Integer version;

    @NotBlank
    @Size(min = 1, max = 50)
    @Column(name = "beer_name")
    private String beerName;

    @NotBlank
    @Size(min = 1, max = 50)
    private String upc;

    @Column(name = "quantity_on_hand")
    private Integer quantityOnHand;

    /**
     * UNIDIRECTIONAL many-to-many with Category.
     * Beer owns the relationship, Category has no back-reference.
     * <p>
     * CascadeType.PERSIST and MERGE allow saving categories with beer,
     * but won't delete categories when beer is deleted (correct behavior).
     * <p>
     * Category is loaded lazily to prevent N+1 queries.
     * We intentionally avoid fetching join / EntityGraph on pageable queries
     * to prevent in-memory pagination issues (HHH90003004).
     * <p>
     * Batch fetching is configured globally via:
     * hibernate.default_batch_fetch_size
     * <p>
     * This allows To Hibernate to load categories in batches using
     * a single "where beer_id in (...)" query instead of N+1.
     */
    @Builder.Default
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "beer_category",
            joinColumns = @JoinColumn(name = "beer_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    /**
     * Add a category to this beer.
     * SIMPLE - no bidirectional sync needed!
     *
     * @param category the category to add
     */
    public void addCategory(Category category) {
        if (category != null) {
            this.categories.add(category);
        }
    }

    /**
     * Remove a category from this beer.
     * SIMPLE - no bidirectional sync needed!
     *
     * @param category the category to remove
     */
    public void removeCategory(Category category) {
        if (category != null) {
            this.categories.remove(category);
        }
    }

    /**
     * Clear all categories from this beer.
     */
    public void clearCategories() {
        this.categories.clear();
    }

    @Positive
    @NotNull
    private BigDecimal price;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Beer)) return false;
        Beer beer = (Beer) o;
        return id != null && id.equals(beer.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}