-- ============================================================
-- V7: Customer addresses + shipment address FK
-- ============================================================
-- customer_addresses stores PERMANENT and SHIPPING addresses.
-- Soft delete (is_active) ensures addresses referenced by
-- beer_order_shipments are never physically removed.
-- ============================================================

CREATE TABLE customer_addresses
(
    address_id  VARCHAR(36)  NOT NULL PRIMARY KEY,
    version     BIGINT,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6),

    customer_id VARCHAR(36)  NOT NULL,
    type        VARCHAR(20)  NOT NULL,   -- PERMANENT | SHIPPING
    street      VARCHAR(150) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    zip         VARCHAR(20)  NOT NULL,
    country     CHAR(2)      NOT NULL,   -- ISO 3166-1 alpha-2 (e.g. 'SK', 'CZ')
    is_active   TINYINT(1)   NOT NULL DEFAULT 1,

    CONSTRAINT fk_addr_customer
        FOREIGN KEY (customer_id) REFERENCES customers (customer_id)
);

ALTER TABLE customer_addresses
    ADD CONSTRAINT chk_address_type
        CHECK (type IN ('PERMANENT', 'SHIPPING'));

-- "all addresses for a customer" — used at checkout and in profile management
CREATE INDEX idx_addr_customer_id ON customer_addresses (customer_id);

-- tax country lookup — used when resolving TaxRate at checkout
CREATE INDEX idx_addr_country ON customer_addresses (country);

-- ============================================================
-- Link beer_order_shipments to the address used at checkout.
-- Nullable: not every shipment has a linked address yet.
-- No ON DELETE CASCADE — addresses use soft delete (is_active).
-- ============================================================
ALTER TABLE beer_order_shipments
    ADD COLUMN address_id VARCHAR(36) NULL,
    ADD CONSTRAINT fk_bos_address
        FOREIGN KEY (address_id) REFERENCES customer_addresses (address_id);
