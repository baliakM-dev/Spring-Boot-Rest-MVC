package com.restmvc.beer_store.services;

import com.restmvc.beer_store.dtos.beerOrder.BeerOrderResponseDTO;
import com.restmvc.beer_store.dtos.cart.AddCartItemRequestDTO;
import com.restmvc.beer_store.dtos.cart.CartResponseDTO;
import com.restmvc.beer_store.dtos.cart.CreateCartRequestDTO;
import com.restmvc.beer_store.dtos.cart.UpdateCartItemQuantityRequestDTO;
import com.restmvc.beer_store.entities.*;
import com.restmvc.beer_store.enums.AddressType;
import com.restmvc.beer_store.enums.CartStatus;
import com.restmvc.beer_store.enums.OrderStatus;
import com.restmvc.beer_store.enums.ShipmentStatus;
import com.restmvc.beer_store.exceptions.ActiveCartAlreadyExistsException;
import com.restmvc.beer_store.exceptions.EmptyCartException;
import com.restmvc.beer_store.exceptions.InsufficientQuantityException;
import com.restmvc.beer_store.exceptions.ResourceNotFoundException;
import com.restmvc.beer_store.mappers.BeerOrderMapper;
import com.restmvc.beer_store.mappers.CartMapper;
import com.restmvc.beer_store.repositories.AddressRepository;
import com.restmvc.beer_store.repositories.BeerOrderRepository;
import com.restmvc.beer_store.repositories.BeerRepository;
import com.restmvc.beer_store.repositories.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service layer for shopping cart operations.
 *
 * <p>Business rules enforced here:
 * <ul>
 *   <li>A customer can have at most ONE ACTIVE cart at a time.</li>
 *   <li>A beer can appear at most once per cart — adding it again increments quantity.</li>
 *   <li>Cart items reference the current beer price at response time (no price snapshot).
 *       Prices are locked in at checkout when a BeerOrderLine is created.</li>
 * </ul>
 * </p>
 *
 * <p>Transaction strategy:
 * <ul>
 *   <li>Read methods use {@code @Transactional(readOnly = true)} — Hibernate skips dirty-checking.</li>
 *   <li>Write methods use {@code @Transactional} (read-write) for atomicity.</li>
 * </ul>
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final BeerRepository beerRepository;
    private final CustomerService customerService;
    private final CartMapper cartMapper;
    private final BeerOrderRepository beerOrderRepository;
    private final BeerOrderMapper beerOrderMapper;
    private final AddressRepository addressRepository;

    /**
     * Creates a new ACTIVE cart for the given customer.
     *
     * <p>Steps:
     * <ol>
     *   <li>Verify the customer exists (throws 404 if not).</li>
     *   <li>Check that the customer has no existing ACTIVE cart (throws 409 if one exists).</li>
     *   <li>Persist a new Cart with status ACTIVE and return the cart representation.</li>
     * </ol>
     * </p>
     *
     * @param customerId           the UUID of the customer creating the cart
     * @param createCartRequestDTO validated DTO with the desired currency (ISO 4217)
     * @return the newly created cart as a {@link CartResponseDTO} (empty items list)
     * @throws ResourceNotFoundException        if no customer with the given UUID exists
     * @throws ActiveCartAlreadyExistsException if the customer already has an ACTIVE cart
     */
    @Transactional
    public CartResponseDTO createCart(UUID customerId, CreateCartRequestDTO createCartRequestDTO) {
        log.info("Creating new cart for customer: {}", customerId);

        // 1) Check if customer exists.
        Customer customer = customerService.getCustomerOrThrow(customerId);

        // 2) Enforce the "1 ACTIVE cart per customer" rule — checked before DB insert
        //    to give a user-friendly 409 instead of a raw DB UNIQUE constraint violation.
        if (cartRepository.existsByCustomer_IdAndStatus(customerId, CartStatus.ACTIVE)) {
            throw new ActiveCartAlreadyExistsException(customerId);
        }

        // 3) Map DTO → entity, set managed associations that cannot come from the DTO, persist.
        Cart cart = cartMapper.dtoToCart(createCartRequestDTO);
        cart.setCustomer(customer);
        cart.setStatus(CartStatus.ACTIVE);

        return cartMapper.entityToCartResponseDTO(cartRepository.save(cart));
    }

    /**
     * Returns the ACTIVE cart for the given customer, including all items.
     *
     * <p>Uses a JOIN FETCH query to load cart items and their beers eagerly in a single
     * SQL query — avoids N+1 and LazyInitializationException during mapping.</p>
     *
     * @param customerId the UUID of the customer
     * @return the active cart as a {@link CartResponseDTO}
     * @throws ResourceNotFoundException if the customer has no ACTIVE cart
     */
    @Transactional(readOnly = true)
    public CartResponseDTO getCartForCustomer(UUID customerId) {
        log.info("Getting active cart for customer: {}", customerId);
        return cartMapper.entityToCartResponseDTO(getActiveCartOrThrow(customerId));
    }

    /**
     * Adds a beer to the customer's ACTIVE cart.
     *
     * <p>Business logic:
     * <ul>
     *   <li>If the beer is already in the cart → increment quantity by the requested amount.</li>
     *   <li>If the beer is new to the cart → create a new CartItem and add it via
     *       {@link Cart#addItem(CartItem)} (keeps both sides of the bidirectional relationship in sync).</li>
     * </ul>
     * </p>
     *
     * <p>Steps:
     * <ol>
     *   <li>Load the ACTIVE cart (with items eagerly fetched) or throw 404.</li>
     *   <li>Load the Beer from the catalog or throw 404.</li>
     *   <li>Find existing CartItem for this beer in the cart.</li>
     *   <li>Increment quantity if found, otherwise create and add a new CartItem.</li>
     *   <li>Save the cart (CascadeType.ALL propagates to CartItem).</li>
     *   <li>Return the updated cart representation.</li>
     * </ol>
     * </p>
     *
     * @param customerId            the UUID of the customer
     * @param addCartItemRequestDTO validated DTO with beerId and quantity to add
     * @return the updated cart as a {@link CartResponseDTO}
     * @throws ResourceNotFoundException if the customer has no ACTIVE cart, or if the beer does not exist
     */
    @Transactional
    public CartResponseDTO addItemToCart(UUID customerId, AddCartItemRequestDTO addCartItemRequestDTO) {
        log.info("Adding item to cart for customer: {}, beer: {}, quantity: {}",
                customerId, addCartItemRequestDTO.beerId(), addCartItemRequestDTO.quantity());

        // 1) Load ACTIVE cart with items + beers eagerly fetched (single JOIN FETCH query).
        Cart cart = getActiveCartOrThrow(customerId);

        // 2) Verify the beer exists in the catalog.
        Beer beer = beerRepository.findById(addCartItemRequestDTO.beerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Beer", "id", addCartItemRequestDTO.beerId().toString()));

        // 3) Check if this beer is already in the cart.
        //    The unique constraint (uk_cart_items_cart_beer) also enforces this at DB level,
        //    but we check here to increment gracefully instead of catching a constraint violation.
        cart.getItems().stream()
                .filter(item -> item.getBeer().getId().equals(beer.getId()))
                .findFirst()
                .ifPresentOrElse(
                        // Beer already in cart → increment quantity on the existing CartItem.
                        existingItem -> {
                            log.info("Beer {} already in cart, incrementing quantity by {}",
                                    beer.getId(), addCartItemRequestDTO.quantity());
                            existingItem.setQuantity(existingItem.getQuantity() + addCartItemRequestDTO.quantity());
                        },
                        // Beer not in cart → build a new CartItem and attach it to the cart.
                        // Cart.addItem() sets item.cart = cart AND adds to cart.items (bidirectional sync).
                        () -> {
                            log.info("Adding new beer {} to cart", beer.getId());
                            CartItem newItem = CartItem.builder()
                                    .beer(beer)
                                    .quantity(addCartItemRequestDTO.quantity())
                                    .build();
                            cart.addItem(newItem);
                        }
                );

        // 4) Saving the cart propagates CascadeType.ALL to any new or modified CartItem.
        Cart savedCart = cartRepository.save(cart);
        return cartMapper.entityToCartResponseDTO(savedCart);
    }

    /**
     * Marks the customer's ACTIVE cart as ABANDONED (soft delete).
     *
     * <p>The cart is NOT physically deleted — it is retained for audit and analytics
     * (e.g. cart abandonment rate metrics). The customer can create a new ACTIVE cart afterwards.</p>
     *
     * @param customerId the UUID of the customer
     * @throws ResourceNotFoundException if the customer has no ACTIVE cart
     */
    @Transactional
    public void deleteActiveCartForCustomer(UUID customerId) {
        log.info("Abandoning active cart for customer: {}", customerId);
        Cart cart = getActiveCartOrThrow(customerId);
        cart.setStatus(CartStatus.ABANDONED);
        // No explicit save() needed — Hibernate dirty-checking detects the status change
        // and issues an UPDATE on transaction commit.
    }

    /**
     * Replaces the quantity of a specific cart item with the given value.
     *
     * <p>This is a full quantity replacement (not an increment) — the new value
     * overwrites the current quantity. To remove an item entirely, use
     * {@link #removeItemFromCart(UUID, UUID)} instead.</p>
     *
     * <p>The item is looked up by {@code itemId} within the customer's ACTIVE cart.
     * This ensures a customer cannot modify items in another customer's cart.</p>
     *
     * @param customerId the UUID of the customer (used to scope the cart lookup)
     * @param itemId     the UUID of the CartItem to update
     * @param requestDTO validated DTO containing the new quantity (1–100)
     * @return the updated cart as a {@link CartResponseDTO}
     * @throws ResourceNotFoundException if the customer has no ACTIVE cart,
     *                                   or if no cart item with the given ID exists in it
     */
    @Transactional
    public CartResponseDTO updateQuantityOfSingleItem(
            UUID customerId,
            UUID itemId,
            UpdateCartItemQuantityRequestDTO requestDTO) {
        log.info("Updating quantity of item {} for customer {} to {}", itemId, customerId, requestDTO.quantity());

        // Load ACTIVE cart with items eagerly — scopes the item lookup to this customer's cart.
        Cart cart = getActiveCartOrThrow(customerId);

        // Find the specific item within this cart (not a global CartItem lookup —
        // that would allow a customer to modify items in another customer's cart).
        CartItem cartItem = cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId.toString()));

        // Replace quantity (this is PATCH — full replacement, not an increment).
        cartItem.setQuantity(requestDTO.quantity());

        // Dirty-checking would also flush this on commit, but explicit save makes intent clear.
        return cartMapper.entityToCartResponseDTO(cartRepository.save(cart));
    }

    /**
     * Removes a single item from the customer's ACTIVE cart.
     *
     * <p>The item is looked up by {@code itemId} within the customer's ACTIVE cart —
     * not globally — so a customer cannot remove items from another customer's cart.</p>
     *
     * <p>{@code orphanRemoval = true} on {@code Cart.items} ensures that removing the item
     * from the collection triggers a physical {@code DELETE} in the database on flush.</p>
     *
     * @param customerId the UUID of the customer (used to scope the cart lookup)
     * @param itemId     the UUID of the CartItem to remove
     * @return the updated cart as a {@link CartResponseDTO} (without the removed item)
     * @throws ResourceNotFoundException if the customer has no ACTIVE cart,
     *                                   or if no item with the given ID exists in it
     */
    @Transactional
    public CartResponseDTO removeItemFromCart(UUID customerId, UUID itemId) {
        log.info("Removing item {} from cart for customer {}", itemId, customerId);

        // Load ACTIVE cart with items eagerly — scopes the item lookup to this customer's cart.
        Cart cart = getActiveCartOrThrow(customerId);

        // Find the specific item within this cart (not a global CartItem lookup —
        // that would allow a customer to modify items in another customer's cart).
        CartItem cartItem = cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId.toString()));

        // Use the aggregate helper — sets cartItem.cart = null AND removes from cart.items.
        // This keeps both sides of the bidirectional relationship in sync for the duration
        // of the transaction. orphanRemoval = true on Cart.items triggers the DELETE in DB.
        cart.removeItem(cartItem);
        return cartMapper.entityToCartResponseDTO(cartRepository.save(cart));
    }
    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @Transactional
    public BeerOrderResponseDTO checkoutActiveCart(UUID customerId) {
        log.info("Checking out active cart for customer: {}", customerId);

        // 1. Načítaj ACTIVE cart (s items + beers eagerly fetched) → 404 ak neexistuje
        Cart cart = getActiveCartOrThrow(customerId);

        // 2. Validuj že košík nie je prázdny → 400
        Set<CartItem> cartItems = cart.getItems();
        if (cartItems.isEmpty()) {
            throw new EmptyCartException(customerId);
        }

        // 3. Validuj zásoby pre VŠETKY items PRED akoukoľvek mutáciou → 422
        //    Nechceme odpočítať zásoby pre prvé 3 items a potom zistiť že 4. nemá dostatok.
        cartItems.forEach(cartItem -> {
            if (cartItem.getBeer().getQuantityOnHand() < cartItem.getQuantity()) {
                throw new InsufficientQuantityException(
                        cartItem.getBeer().getId(),
                        cartItem.getQuantity(),
                        cartItem.getBeer().getQuantityOnHand()
                );
            }
        });

        // 4. Vytvor BeerOrder — amounts nastavíme na ZERO, prepočítame po pridaní lines
        BeerOrder order = BeerOrder.builder()
                .customer(cart.getCustomer())
                .status(OrderStatus.NEW)
                .currency(cart.getCurrency())
                .subtotalAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)  // bez promo
                .shippingAmount(BigDecimal.ZERO)  // flat rate, doplníš neskôr
                .taxAmount(BigDecimal.ZERO)        // doplníš neskôr
                .totalAmount(BigDecimal.ZERO)
                .build();

        // 5. Vytvor BeerOrderLine pre každý CartItem + 6. Odpočítaj zásoby
        //    Zásoby meníme až tu — až po úspešnej validácii všetkých items.
        cartItems.forEach(cartItem -> {
            Beer beer = cartItem.getBeer();

            BeerOrderLine line = BeerOrderLine.builder()
                    .beer(beer)
                    .orderQuantity(cartItem.getQuantity())
                    .unitPrice(beer.getPrice())   // price SNAPSHOT v tomto momente
                    .build();
            line.recalcSubtotal();    // lineSubtotal = unitPrice * orderQuantity
            order.addLine(line);      // nastaví line.beerOrder = order (bidirectional sync)

            // Odpočítaj zásoby
            beer.setQuantityOnHand(beer.getQuantityOnHand() - cartItem.getQuantity());
        });

        // Skontroluj dorucovaciu adresu ak nieje pouzi permanent ak nieje 404 adresa nieje zadana
        // Načítaj len aktívne adresy
        List<Address> addresses = addressRepository.findByCustomerIdAndIsActiveTrue(customerId);

        if (addresses.isEmpty()) {
            throw new ResourceNotFoundException("Address", "customerId", customerId.toString());
        }

        // 1. Preferuj SHIPPING, fallback na PERMANENT, inak exception
        Address deliveryAddress = addresses.stream()
                .filter(a -> a.getType() == AddressType.SHIPPING)
                .findFirst()
                .orElseGet(() -> addresses.stream()
                        .filter(a -> a.getType() == AddressType.PERMANENT)
                        .findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Address", "customerId", customerId.toString()))
                );

        // 2. Vytvor shipment s nájdenou adresou
        BeerOrderShipment shipment = BeerOrderShipment.builder()
                .address(deliveryAddress)
                .status(ShipmentStatus.NEW)
                .build();
        order.setShipment(shipment);

        // Prepočítaj subtotalAmount ako súčet všetkých lineSubtotal
        BigDecimal subtotal = order.getLines().stream()
                .map(BeerOrderLine::getLineSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotalAmount(subtotal);
        order.recalculateTotals();    // totalAmount = subtotal - discount + shipping + tax

        // 7. Zmeň status košíka na CHECKED_OUT (dirty-checking — save() nie je potrebný)
        cart.setStatus(CartStatus.CHECKED_OUT);

        // 8. Ulož BeerOrder — CascadeType.ALL propaguje aj BeerOrderLines
        //    Pozn.: Cart nemá cascade na BeerOrder — sú to separátne aggregate roots.
        BeerOrder savedOrder = beerOrderRepository.save(order);

        // 9. Beer entity sú managed v tejto transakcii — Hibernate dirty-checking
        //    automaticky vydá UPDATE pre zmenený quantityOnHand pri flushu.

        // 10. Namapuj a vráť DTO
        return beerOrderMapper.toResponseDTO(savedOrder);
    }

    /**
     * Loads the customer's ACTIVE cart with all items and beers eagerly fetched.
     *
     * <p>Uses a JOIN FETCH JPQL query so that {@code cart.items} and {@code item.beer}
     * are available for mapping without a second round-trip or LazyInitializationException.</p>
     *
     * @param customerId the UUID of the customer
     * @return the active {@link Cart} with items loaded
     * @throws ResourceNotFoundException if no ACTIVE cart exists for this customer
     */
    private Cart getActiveCartOrThrow(UUID customerId) {
        return cartRepository.findActiveCartWithItemsByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active cart for customer", "customerId", customerId.toString()));
    }
}
