-- V25: Mesas del restaurante (MVP).
-- Cada mesa tiene un código UUID único global (code) que va en el QR.
-- El frontend arma la URL {APP_BASE_URL}/menu/{slug}?t={code} y el backend
-- valida el code para saber la mesa real y el restaurante al que pertenece.
-- Sin campos extra (sin active/position): DELETE físico, los pedidos
-- históricos conservan table_number como snapshot y table_id pasa a NULL.

CREATE TABLE restaurant_tables (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT      NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    label         VARCHAR(30) NOT NULL,
    code          UUID        NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tables_restaurant_label UNIQUE (restaurant_id, label)
);

CREATE INDEX idx_tables_restaurant ON restaurant_tables (restaurant_id);

-- Trazabilidad pedido -> mesa. table_number existente queda como snapshot
-- del label (si la mesa se renombra o se elimina, el pedido conserva el dato).
ALTER TABLE orders
    ADD COLUMN table_id BIGINT REFERENCES restaurant_tables (id) ON DELETE SET NULL;

CREATE INDEX idx_orders_table ON orders (table_id);
