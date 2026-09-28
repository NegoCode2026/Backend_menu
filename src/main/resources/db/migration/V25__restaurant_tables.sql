-- V25: Mesas del restaurante (salón compartido por todo el equipo).
-- Antes vivían en el localStorage de cada navegador: lo creado en un
-- dispositivo no aparecía en los demás. Ahora son del tenant.
CREATE TABLE restaurant_tables (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    number        VARCHAR(20)  NOT NULL,
    seats         INTEGER      NOT NULL DEFAULT 2,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_restaurant_table_number UNIQUE (restaurant_id, number)
);

CREATE INDEX idx_restaurant_tables_lookup ON restaurant_tables (restaurant_id);
