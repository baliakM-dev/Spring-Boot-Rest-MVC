package com.restmvc.beer_store.repositories;

import com.restmvc.beer_store.entities.Cart;
import com.restmvc.beer_store.enums.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Cart} entities.
 *
 * <p>A customer can have at most ONE {@code ACTIVE} cart at any time.
 * This rule is enforced in the service layer. Historical carts
 * ({@code CHECKED_OUT}, {@code ABANDONED}) are retained for audit purposes.</p>
 */
public interface CartRepository extends JpaRepository<Cart, UUID> {

    /**
     * Returns {@code true} if the customer already has a cart with the given status.
     * Used by {@code CartService.createCart} to prevent creating a second ACTIVE cart.
     */
    boolean existsByCustomer_IdAndStatus(UUID customerId, CartStatus status);

    /**
     * Loads the customer's ACTIVE cart together with its items and each item's beer.
     *
     * <p>Uses a JOIN FETCH to load {@code cart.items} and {@code item.beer} in a single
     * query, avoiding LazyInitializationException when the mapper accesses those
     * associations outside the original transaction context.</p>
     *
     * <p>Only the ACTIVE cart is returned — CHECKED_OUT and ABANDONED carts are excluded.
     * Returns empty if the customer has no active cart.</p>
     *
     * @param customerId the UUID of the customer
     * @return an Optional containing the active cart with items eagerly loaded, or empty
     */
    @Query("""
            select c from Cart c
            left join fetch c.items i
            left join fetch i.beer
            where c.customer.id = :customerId
              and c.status = 'ACTIVE'
            """)
    Optional<Cart> findActiveCartWithItemsByCustomerId(UUID customerId);
}
