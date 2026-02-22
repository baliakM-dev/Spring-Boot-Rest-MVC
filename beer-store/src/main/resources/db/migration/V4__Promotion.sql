-- ============================================================
-- Promotion system
--
-- Design decisions:
--
-- 1. Separate promotions table — promotions are a shared catalog entity,
--    not owned by any single order. One promo code can be used across many orders.
--
-- 2. Snapshot pattern on beer_orders:
--    When a promotion is applied at checkout, three fields are copied from the
--    promotions row onto the beer_orders row (promo_code, promo_discount_type,
--    promo_discount_value). This preserves the exact terms of the discount even
--    if the promotion is later edited or deactivated.
--    discount_amount stores the final computed deduction.
--
-- 3. Never hard-delete used promotions:
--    beer_orders.promotion_id FK (without ON DELETE CASCADE) means attempting to
--    delete a promotion that has been applied to orders will fail with a FK violation.
--    Deactivate promotions via the active flag instead.
--
-- 4. Optimistic locking on promotions:
--    used_count is incremented on every checkout. The @Version column protects
--    against concurrent use of the same promo code losing increments.
-- ============================================================

create table promotions
(
    promotion_id   varchar(36)    not null primary key,
    version        bigint,
    created_at     timestamp(6),
    updated_at     timestamp(6),

    -- Human-readable code entered by the customer (e.g. "SUMMER10").
    -- Stored and compared uppercase — service must normalise input before lookup.
    code           varchar(50)    not null,

    -- Optional label for admin UI (e.g. "Summer sale 2026").
    description    varchar(100),

    -- How discount_value is applied:
    --   PERCENTAGE   → value = % to deduct from subtotal (e.g. 10.00 = 10%)
    --   FIXED_AMOUNT → value = flat monetary deduction (e.g. 5.00 = 5 EUR)
    discount_type  varchar(20)    not null,

    -- Magnitude of the discount. Always > 0.
    -- For PERCENTAGE: capped at 100 by the app; for FIXED_AMOUNT: capped at subtotal.
    discount_value numeric(38, 2) not null,

    -- Maximum number of times this promotion can be applied across all orders.
    -- NULL = unlimited uses.
    max_uses       int,

    -- Running count of how many orders have used this promotion.
    -- Protected against concurrent double-use by optimistic locking (version column).
    used_count     int            not null default 0,

    -- Promotion is valid only within this date/time window (both nullable = no limit).
    valid_from     timestamp(6),
    valid_until    timestamp(6),

    -- Master on/off switch. Deactivate instead of deleting to preserve audit trail.
    active         boolean        not null default true,

    constraint uk_promotions_code unique (code)
);

alter table promotions
    add constraint chk_promotions_discount_type
        check (discount_type in ('PERCENTAGE', 'FIXED_AMOUNT'));

alter table promotions
    add constraint chk_promotions_value
        check (discount_value > 0);

alter table promotions
    add constraint chk_promotions_used_count
        check (used_count >= 0);

alter table promotions
    add constraint chk_promotions_max_uses
        check (max_uses is null or max_uses > 0);

-- Primary lookup: customer enters code → find promotion by code.
create index idx_promotions_code on promotions (code);

-- Admin query: "show all currently active promotions in a date range".
create index idx_promotions_active_valid on promotions (active, valid_from, valid_until);

-- ============================================================
-- Add promotion FK + snapshot columns to beer_orders
-- ============================================================

-- Nullable FK to the live Promotion entity.
-- Used for admin/reporting queries (join to get promotion details).
-- No ON DELETE CASCADE — must deactivate promotions instead of deleting them.
alter table beer_orders
    add column promotion_id varchar(36) null,
    add constraint fk_beer_orders_promotion
        foreign key (promotion_id) references promotions (promotion_id);

-- Snapshot: the code the customer typed at checkout (e.g. "SUMMER10").
-- Preserved even if promotions.code is changed after the order was placed.
alter table beer_orders
    add column promo_code varchar(50) null;

-- Snapshot: how the discount was calculated (PERCENTAGE or FIXED_AMOUNT).
alter table beer_orders
    add column promo_discount_type varchar(20) null;

alter table beer_orders
    add constraint chk_beer_orders_promo_discount_type
        check (promo_discount_type is null or promo_discount_type in ('PERCENTAGE', 'FIXED_AMOUNT'));

-- Snapshot: the raw discount magnitude at time of order (e.g. 10.00 for 10% or 10 EUR).
alter table beer_orders
    add column promo_discount_value numeric(38, 2) null;

-- Index: useful for querying "all orders that used a specific promotion".
create index idx_beer_orders_promotion_id on beer_orders (promotion_id);
