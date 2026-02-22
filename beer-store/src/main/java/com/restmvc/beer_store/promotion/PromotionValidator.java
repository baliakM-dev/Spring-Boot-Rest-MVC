package com.restmvc.beer_store.promotion;

import com.restmvc.beer_store.entities.Promotion;
import com.restmvc.beer_store.exceptions.PromoNotValidException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Stateless Spring component responsible for validating whether a {@link Promotion}
 * can be applied to an order at a given point in time.
 *
 * <p>SRP: This class has exactly one reason to change — the business rules that determine
 * promotion validity. It knows nothing about orders, carts, or persistence.</p>
 *
 * <p>Validation rules (checked in order):
 * <ol>
 *   <li>Promotion must be active (active = true).</li>
 *   <li>Current time must be on or after valid_from (if set).</li>
 *   <li>Current time must be on or before valid_until (if set).</li>
 *   <li>used_count must be less than max_uses (if max_uses is set).</li>
 * </ol>
 * Each rule has its own reason string so the API can return a meaningful error message.
 * </p>
 *
 * <p>Usage:
 * <pre>
 *   promotionValidator.validate(promotion, LocalDateTime.now());
 *   // throws PromoNotValidException with a specific reason if invalid
 *   // returns normally if valid
 * </pre>
 * </p>
 */
@Component
public class PromotionValidator {

    /**
     * Validates that the promotion is applicable at the given moment.
     * Throws {@link PromoNotValidException} with a specific reason on the first failed rule.
     *
     * @param promo the promotion to validate (must not be null)
     * @param now   the current date/time used for date range checks (inject for testability)
     * @throws PromoNotValidException if any validity rule is violated
     */
    public void validate(Promotion promo, LocalDateTime now) {
        // Rule 1: admin must not have deactivated the promotion.
        if (!Boolean.TRUE.equals(promo.getActive())) {
            throw new PromoNotValidException(promo.getCode(), "promotion is no longer active");
        }

        // Rule 2: promotion period has not started yet.
        if (promo.getValidFrom() != null && now.isBefore(promo.getValidFrom())) {
            throw new PromoNotValidException(
                    promo.getCode(),
                    "promotion is not valid yet, starts at " + promo.getValidFrom()
            );
        }

        // Rule 3: promotion period has already ended.
        if (promo.getValidUntil() != null && now.isAfter(promo.getValidUntil())) {
            throw new PromoNotValidException(
                    promo.getCode(),
                    "promotion expired at " + promo.getValidUntil()
            );
        }

        // Rule 4: usage cap has been reached.
        if (promo.getMaxUses() != null && promo.getUsedCount() >= promo.getMaxUses()) {
            throw new PromoNotValidException(
                    promo.getCode(),
                    "promotion has reached its maximum number of uses (" + promo.getMaxUses() + ")"
            );
        }
    }
}
