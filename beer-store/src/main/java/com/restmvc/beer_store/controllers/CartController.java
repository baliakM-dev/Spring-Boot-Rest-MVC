package com.restmvc.beer_store.controllers;

import com.restmvc.beer_store.dtos.beerOrder.BeerOrderResponseDTO;
import com.restmvc.beer_store.dtos.cart.AddCartItemRequestDTO;
import com.restmvc.beer_store.dtos.cart.CartResponseDTO;
import com.restmvc.beer_store.dtos.cart.CreateCartRequestDTO;
import com.restmvc.beer_store.dtos.cart.UpdateCartItemQuantityRequestDTO;
import com.restmvc.beer_store.services.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller for managing a customer's shopping cart.
 *
 * <p>All endpoints are scoped to a specific customer via the {@code customerId} path variable.
 * Business logic is delegated entirely to {@link CartService} — the controller is intentionally
 * thin (no business logic, no direct repository access).</p>
 *
 * <p>Cart lifecycle and HTTP status conventions:
 * <ul>
 *   <li>POST /cart → 201 Created (Location: /cart, body: created cart)</li>
 *   <li>GET /cart → 200 OK (active cart with items)</li>
 *   <li>POST /cart/items → 200 OK (full updated cart after add/increment)</li>
 *   <li>DELETE /cart       → 204 No Content</li>
 * </ul>
 * </p>
 *
 * <p>Why POST /cart/items returns 200 and not 201:
 * Adding an item may either create a new CartItem OR increment an existing one.
 * Since both cases return the same response (the full updated cart), 200 OK is used
 * uniformly to avoid a conditional status code that would confuse clients.</p>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public class CartController {

    /**
     * Base path for cart endpoints, scoped to a customer.
     */
    public static final String BASE_URL = "/api/v1/customers/{customerId}/cart";

    /**
     * Endpoint for adding items to the cart.
     */
    public static final String BASE_URL_ITEMS = BASE_URL + "/items";

    /**
     * Endpoint for updating or removing a single cart item.
     */
    public static final String BASE_URL_ITEMS_ID = BASE_URL_ITEMS + "/{itemId}";

    /**
     * Endpoint for triggering checkout (cart → order).
     */
    public static final String BASE_URL_CHECKOUT = BASE_URL + "/checkout";

    /**
     * Endpoint for applying or removing a promo code.
     */
    public static final String BASE_URL_PROMO = BASE_URL + "/promo";

    // Only the service is injected — direct repository access belongs in the service layer (SRP).
    private final CartService cartService;

    /**
     * Creates a new ACTIVE cart for the given customer.
     *
     * <p>A customer can have at most one ACTIVE cart — if one already exists,
     * the service returns 409 Conflict. Previous CHECKED_OUT or ABANDONED carts are unaffected.</p>
     *
     * @param customerId           the customer's UUID (from the URL path)
     * @param createCartRequestDTO validated body with the cart's currency (ISO 4217, e.g. "EUR")
     * @return 201 Created with the new cart in the body and a Location header
     */
    @PostMapping(BASE_URL)
    public ResponseEntity<CartResponseDTO> createCartForCustomer(
            @PathVariable UUID customerId,
            @Valid @RequestBody CreateCartRequestDTO createCartRequestDTO) {
        log.info("Creating cart for customer: {}", customerId);
        CartResponseDTO created = cartService.createCart(customerId, createCartRequestDTO);
        URI location = URI.create("/api/v1/customers/" + customerId + "/cart");
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Returns the customer's ACTIVE cart including all items and the cart total.
     *
     * @param customerId the customer's UUID (from the URL path)
     * @return 200 OK with the active cart, or 404 if no active cart exists
     */
    @GetMapping(BASE_URL)
    public ResponseEntity<CartResponseDTO> getActiveCartForCustomer(
            @PathVariable UUID customerId) {
        log.info("Getting cart for customer: {}", customerId);
        return ResponseEntity.ok(cartService.getCartForCustomer(customerId));
    }

    /**
     * Adds a beer to the customer's ACTIVE cart.
     *
     * <p>Two cases handled by the service:
     * <ul>
     *   <li>Beer already in cart → quantity is incremented by the requested amount.</li>
     *   <li>Beer not in cart → a new cart item is created.</li>
     * </ul>
     * Either way, the full updated cart (with all items and the recalculated total) is returned.</p>
     *
     * @param customerId            the customer's UUID (from the URL path)
     * @param addCartItemRequestDTO validated body with {@code beerId} and {@code quantity} (1–100)
     * @return 200 OK with the updated cart
     */
    @PostMapping(BASE_URL_ITEMS)
    public ResponseEntity<CartResponseDTO> addItemToCart(
            @PathVariable UUID customerId,
            @Valid @RequestBody AddCartItemRequestDTO addCartItemRequestDTO) {
        log.info("Adding item to cart for customer: {}, beer: {}, qty: {}",
                customerId, addCartItemRequestDTO.beerId(), addCartItemRequestDTO.quantity());
        return ResponseEntity.ok(cartService.addItemToCart(customerId, addCartItemRequestDTO));
    }

    /**
     * Abandons (soft-deletes) the customer's ACTIVE cart.
     *
     * <p>The cart's status changes to ABANDONED — it is NOT physically deleted so that
     * cart abandonment metrics remain accurate. The customer can open a new cart afterwards.</p>
     *
     * @param customerId the customer's UUID (from the URL path)
     * @return 204 No Content on success, or 404 if the customer has no active cart
     */
    @DeleteMapping(BASE_URL)
    public ResponseEntity<Void> deleteActiveCartForCustomer(
            @PathVariable UUID customerId) {
        log.info("Abandoning cart for customer: {}", customerId);
        cartService.deleteActiveCartForCustomer(customerId);
        return ResponseEntity.noContent().build();
    }

    // TODO: PATCH  /cart/items/{itemId} — update quantity of a single item
    @PatchMapping(BASE_URL_ITEMS_ID)
    public ResponseEntity<CartResponseDTO> updateQuantityOfSingleItem(
            @PathVariable UUID customerId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemQuantityRequestDTO requestDTO) {
        log.info("Updating quantity of item {} for customer {}: {}", itemId, customerId, requestDTO.quantity());
        return ResponseEntity.ok(cartService.updateQuantityOfSingleItem(customerId, itemId, requestDTO));
    }

    /**
     * Removes a single item from the customer's ACTIVE cart.
     *
     * <p>Returns 200 OK with the updated cart (not 204) so the client immediately
     * sees the new cart state (remaining items + recalculated total) without a
     * follow-up GET request. This is consistent with how PATCH /cart/items/{itemId} works.</p>
     *
     * @param customerId the customer's UUID (from the URL path)
     * @param itemId     the UUID of the CartItem to remove (from the URL path)
     * @return 200 OK with the updated cart, or 404 if the cart or item does not exist
     */
    @DeleteMapping(BASE_URL_ITEMS_ID)
    public ResponseEntity<CartResponseDTO> removeItemFromCart(
            @PathVariable UUID customerId,
            @PathVariable UUID itemId) {
        log.info("Removing item {} from cart for customer {}", itemId, customerId);
        return ResponseEntity.ok(cartService.removeItemFromCart(customerId, itemId));
    }

    // TODO: POST   /cart/checkout        — convert active cart to a BeerOrder
    @PostMapping(BASE_URL_CHECKOUT)
    public ResponseEntity<BeerOrderResponseDTO> checkoutActiveCart(
            @PathVariable UUID customerId) {
        log.info("Checking out active cart for customer {}", customerId);
        return ResponseEntity.ok(cartService.checkoutActiveCart(customerId));
    }

    // TODO: POST   /cart/promo           — apply a promotion code to the cart
    // TODO: DELETE /cart/promo           — remove the applied promotion code
}
