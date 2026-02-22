package com.restmvc.beer_store.dtos.beerOrder;

import com.restmvc.beer_store.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BeerOrderResponseDTO(
        UUID id,
        UUID customerId,
        OrderStatus status,
        String currency,
        String promoCode,           // null ak nebola promo
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,  // ZERO ak nebola promo
        BigDecimal shippingAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,     // subtotal - discount + shipping + tax
        List<BeerOrderLineResponseDTO> lines,
        LocalDateTime createdAt
) {}
