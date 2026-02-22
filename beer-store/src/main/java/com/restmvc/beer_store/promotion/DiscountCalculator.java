package com.restmvc.beer_store.promotion;

import com.restmvc.beer_store.enums.DiscountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stateless Spring component responsible for computing the monetary discount amount
 * from a promotion's type and value applied to a given order subtotal.
 *
 * <p>SRP: This class has exactly one reason to change — the discount calculation formula.
 * It knows nothing about promotions, orders, or persistence.</p>
 *
 * <p>Usage (inject via constructor in service classes):
 * <pre>
 *   BigDecimal discount = discountCalculator.calculate(
 *       promo.getDiscountType(), promo.getDiscountValue(), order.getSubtotalAmount()
 *   );
 * </pre>
 * </p>
 *
 * <p>Calculation rules:
 * <ul>
 *   <li>PERCENTAGE: {@code discount = subtotal * (value / 100)}, rounded HALF_UP to 2 decimal places.</li>
 *   <li>FIXED_AMOUNT: {@code discount = min(value, subtotal)} — never exceeds the subtotal.</li>
 *   <li>In both cases the result is additionally capped at subtotal as a safety net.</li>
 *   <li>If subtotal is null, zero, or negative, the result is always ZERO.</li>
 * </ul>
 * </p>
 */
@Component
public class DiscountCalculator {

    /**
     * Computes the discount amount to deduct from the given subtotal.
     *
     * @param type     how the discount value is interpreted (PERCENTAGE or FIXED_AMOUNT)
     * @param value    the magnitude of the discount (e.g. 10.00 for 10% or 10 EUR)
     * @param subtotal the order subtotal before any deductions (must be &gt; 0)
     * @return the discount amount, always &gt;= 0 and &lt;= subtotal; never null
     */
    public BigDecimal calculate(DiscountType type, BigDecimal value, BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount = switch (type) {
            // Percentage: e.g. value=10.00 → 10% of subtotal, rounded to cents (HALF_UP).
            case PERCENTAGE -> subtotal
                    .multiply(value)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // Fixed: e.g. value=5.00 → deduct exactly 5 EUR, but never more than subtotal.
            case FIXED_AMOUNT -> value.min(subtotal);
        };

        // Final safety cap — result can never be negative or exceed the subtotal.
        return discount.min(subtotal).max(BigDecimal.ZERO);
    }
}
