-- ============================================================
-- MySQL note on "one ACTIVE cart per customer" uniqueness:
--
-- Ideal solution would be a partial unique index:
--   CREATE UNIQUE INDEX uk_carts_customer_active ON carts (customer_id) WHERE status = 'ACTIVE';
-- BUT MySQL does NOT support partial (filtered) indexes.
--
-- GENERATED STORED column + UNIQUE index also fails on MySQL because
-- MySQL does not allow foreign keys to reference generated columns,
-- causing error 1215 (Cannot add foreign key constraint).
--
-- The chosen approach: enforce "one ACTIVE cart per customer" in the SERVICE LAYER.
-- The service must check for an existing ACTIVE cart before creating a new one
-- and throw a business exception if one already exists.
--
-- PostgreSQL and H2 both support partial indexes natively — no workaround needed there.
-- ============================================================

create table carts
(
    cart_id     varchar(36) not null primary key,
    customer_id varchar(36) not null,
    status      varchar(20) not null default 'ACTIVE',
    created_at  timestamp(6),
    updated_at  timestamp(6),
    version     bigint,

    -- Every cart must belong to an existing customer.
    -- ON DELETE CASCADE: removing a customer removes all their carts (and items via cascade).
    constraint fk_carts_customer
        foreign key (customer_id) references customers (customer_id)
            on delete cascade
);

alter table carts
    add constraint chk_carts_status
        check (status in ('ACTIVE', 'CHECKED_OUT', 'ABANDONED'));

-- Speeds up "all carts for a customer" and active-cart lookups.
create index idx_carts_customer_id on carts (customer_id);

create table cart_items
(
    cart_item_id varchar(36) not null primary key,
    cart_id      varchar(36) not null,
    beer_id      varchar(36) not null,
    quantity     int         not null,

    created_at   timestamp(6),
    updated_at   timestamp(6),
    version      bigint,

    -- ON DELETE CASCADE: removing a cart removes all its items automatically.
    constraint fk_cart_items_cart
        foreign key (cart_id) references carts (cart_id)
            on delete cascade,

    -- No cascade on beer: deleting a cart item must NOT delete the beer from the catalog.
    -- Deleting a Beer that is still in a cart will fail with a FK violation (intentional guard).
    constraint fk_cart_items_beer
        foreign key (beer_id) references beers (beer_id),

    -- One beer can appear at most once per cart — service layer increments quantity in-place.
    constraint uk_cart_items_cart_beer unique (cart_id, beer_id),

    -- Quantity must be at least 1 — zero-quantity items must be deleted, not stored.
    constraint chk_cart_items_qty check (quantity > 0)
);

-- Speeds up "all items in a cart" queries (used on every cart load).
create index idx_cart_items_cart_id on cart_items (cart_id);
-- Speeds up "which carts contain this beer" queries (stock checks, beer deletion guards).
create index idx_cart_items_beer_id on cart_items (beer_id);
