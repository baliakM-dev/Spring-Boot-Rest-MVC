create table beers
(
    beer_id          varchar(36)    not null primary key,
    beer_name        varchar(50)    not null,
    upc              varchar(50)    not null,
    price            numeric(38, 2) not null check (price >= 0),
    quantity_on_hand integer,
    version          integer default null,
    created_at       timestamp(6),
    updated_at       timestamp(6),
    constraint uk_beers_beer_name unique (beer_name)
);

create index idx_beers_upc on beers (upc);
create index idx_beers_created_at on beers (created_at);


create table customers
(
    customer_id varchar(36)  not null primary key,
    name        varchar(100) not null,
    version     integer default null,
    created_at  timestamp(6),
    updated_at  timestamp(6),
    constraint uk_customers_name unique (name)
);

create index idx_customers_created_at on customers (created_at);


create table categories
(
    category_id  varchar(36) not null primary key,
    description  varchar(50) not null,
    version      integer default null,
    created_at   timestamp(6),
    updated_at   timestamp(6),
    constraint uk_categories_description unique (description)
);

create index idx_categories_created_at on categories (created_at);


create table beer_category
(
    beer_id     varchar(36) not null,
    category_id varchar(36) not null,
    primary key (beer_id, category_id),
    constraint fk_beer_category_beer
        foreign key (beer_id) references beers (beer_id) on delete cascade,
    constraint fk_beer_category_category
        foreign key (category_id) references categories (category_id) on delete cascade
);

create index idx_beer_category_category_id on beer_category (category_id);
create index idx_beer_category_beer_id on beer_category (beer_id);