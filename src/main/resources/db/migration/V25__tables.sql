CREATE TABLE IF NOT EXISTS restaurant_tables (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    table_number  VARCHAR(50)  NOT NULL,
    seats         INT          NOT NULL DEFAULT 2,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_restaurant_tables_number UNIQUE (restaurant_id, table_number)
);

CREATE INDEX idx_restaurant_tables_restaurant ON restaurant_tables (restaurant_id);
