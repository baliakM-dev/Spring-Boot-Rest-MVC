CREATE TABLE beer_orders
(
    beer_order_id   VARCHAR(36)    NOT NULL PRIMARY KEY,
    created_at      TIMESTAMP(6),
    updated_at      TIMESTAMP(6),
    version         BIGINT,
    customer_id     VARCHAR(36)    NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'NEW',
    currency        VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    subtotal_amount NUMERIC(38, 2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(38, 2) NOT NULL DEFAULT 0,
    shipping_amount NUMERIC(38, 2) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(38, 2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(38, 2) NOT NULL DEFAULT 0,
        CONSTRAINT fk_beer_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id)
);

CREATE INDEX idx_beer_orders_customer_id ON beer_orders (customer_id);
CREATE INDEX idx_beer_orders_created_at ON beer_orders (created_at);
CREATE INDEX idx_beer_orders_customer_created ON beer_orders (customer_id, created_at);

ALTER TABLE beer_orders
    ADD CONSTRAINT chk_beer_orders_amounts_nonneg
        CHECK (
            subtotal_amount >= 0 AND
            discount_amount >= 0 AND
            shipping_amount >= 0 AND
            tax_amount >= 0 AND
            total_amount >= 0
            );

CREATE TABLE beer_order_lines
(
    beer_order_line_id VARCHAR(36)    NOT NULL PRIMARY KEY,
    created_at         TIMESTAMP(6),
    updated_at         TIMESTAMP(6),
    version            BIGINT,
    beer_order_id      VARCHAR(36)    NOT NULL,
    beer_id            VARCHAR(36)    NOT NULL,
    order_quantity     INT            NOT NULL,
    quantity_allocated INT,
    unit_price         NUMERIC(38, 2) NOT NULL,
    line_subtotal      NUMERIC(38, 2) NOT NULL DEFAULT 0,
        CONSTRAINT fk_bol_order FOREIGN KEY (beer_order_id) REFERENCES beer_orders (beer_order_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_bol_beer
        FOREIGN KEY (beer_id) REFERENCES beers (beer_id)
);

CREATE INDEX idx_bol_order_id ON beer_order_lines (beer_order_id);
CREATE INDEX idx_bol_beer_id ON beer_order_lines (beer_id);

ALTER TABLE beer_order_lines
    ADD CONSTRAINT chk_bol_prices_nonneg
        CHECK (unit_price >= 0 AND line_subtotal >= 0);

CREATE TABLE beer_order_shipments
(
    beer_order_shipment_id VARCHAR(36) NOT NULL PRIMARY KEY,
    beer_order_id          VARCHAR(36) NOT NULL UNIQUE,
    tracking_number        VARCHAR(50),
    status                 VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at             TIMESTAMP(6),
    updated_at             TIMESTAMP(6),
    version                BIGINT,
    CONSTRAINT fk_bos_order
        FOREIGN KEY (beer_order_id) REFERENCES beer_orders (beer_order_id)
            ON DELETE CASCADE
);

ALTER TABLE beer_order_shipments
    ADD CONSTRAINT chk_shipment_status
        CHECK (status IN ('NEW', 'SENT', 'IN_TRANSIT', 'DELIVERED'));

CREATE INDEX idx_bos_tracking_number ON beer_order_shipments (tracking_number);

ALTER TABLE beer_orders
    ADD CONSTRAINT chk_order_status
        CHECK (status IN ('NEW', 'PAID', 'CANCELLED', 'SHIPPED'));