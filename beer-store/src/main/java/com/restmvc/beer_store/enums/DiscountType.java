package com.restmvc.beer_store.enums;

/**
 * Defines how a promotion's discount value is interpreted.
 * <p>
 * PERCENTAGE — discount is a percentage of subtotal_amount.
 * Example: value = 10.00 → 10% off subtotal.
 * Capped at 100.00 (cannot exceed 100%).
 * <p>
 * FIXED_AMOUNT — discount is a fixed monetary value deducted from subtotal_amount.
 * Example: value = 5.00 → 5 EUR off the order.
 * Capped at subtotal_amount (discount cannot make total negative).
 */
public enum DiscountType {
    PERCENTAGE,
    FIXED_AMOUNT
}
