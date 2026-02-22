alter table carts
    add currency
        VARCHAR(3) NOT NULL DEFAULT 'EUR';