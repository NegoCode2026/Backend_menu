-- V11: Inventario (stock + alertas), costos para utilidades, snapshot de costo
-- en items, movimientos de stock y roles de staff (mesero/caja).

-- Productos: costo, existencias, umbral de alerta y flag de rastreo.
-- track_stock=FALSE por defecto para no romper el catálogo existente
-- (un producto sin stock configurado sigue vendiéndose normal).
ALTER TABLE products
    ADD COLUMN cost_price NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN stock_quantity INT NOT NULL DEFAULT 0,
    ADD COLUMN low_stock_threshold INT NOT NULL DEFAULT 5,
    ADD COLUMN track_stock BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_products_restaurant_stock ON products (restaurant_id, track_stock);

-- Snapshot del costo al momento del pedido: las utilidades históricas no
-- cambian aunque el costo del producto se actualice después.
ALTER TABLE order_items
    ADD COLUMN unit_cost NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Kardex: auditoría de cada entrada/salida de inventario.
CREATE TABLE stock_movements (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    product_id    BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    quantity      INT          NOT NULL,
    reason        VARCHAR(30)  NOT NULL DEFAULT 'ADJUST',
    order_id      BIGINT       REFERENCES orders (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_movements_restaurant ON stock_movements (restaurant_id, created_at DESC);
CREATE INDEX idx_stock_movements_product ON stock_movements (product_id, created_at DESC);

-- Roles operativos del restaurante (mesero/caja). El login y el WebSocket
-- los distinguen; los permisos de gestión siguen en RESTAURANT_ADMIN.
INSERT INTO roles (name, description) VALUES
    ('WAITER',  'Mesero: atiende pedidos y mesas'),
    ('CASHIER', 'Cajero: cobra y cierra pedidos')
ON CONFLICT (name) DO NOTHING;
