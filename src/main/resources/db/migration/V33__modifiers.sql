-- Migration V33: modifiers y variantes
--
-- Faltaba por completo (0 referencias en el proyecto). Sin esto un restaurante
-- no puede publicar su carta real: no hay forma de vender "tamaño pequeño /
-- mediano / grande", "término de la carne" ni "sin cebolla, extra queso".
--
-- Diseño:
--   * Un grupo pertenece al restaurante y define cuántas opciones se pueden
--     elegir (min/max) y si es obligatorio. "Término" sería obligatorio con
--     exactamente 1; "Extras" opcional de 0 a 3.
--   * Un modificador es una opción concreta con su delta de precio.
--   * product_modifier_groups relaciona qué grupos ofrece cada producto.
--   * order_item_modifiers guarda lo que eligió el cliente CON SU PRECIO
--     CONGELADO. Importante: product_id en order_items es ON DELETE SET NULL,
--     así que si el restaurante cambia el precio o borra una opción, un pedido
--     viejo debe seguir cuadrando. Por eso se guarda nombre y delta, no solo el id.

CREATE TABLE IF NOT EXISTS modifier_groups (
    id               BIGSERIAL PRIMARY KEY,
    restaurant_id    BIGINT      NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    name             VARCHAR(120) NOT NULL,
    description      VARCHAR(255),
    min_selections   INTEGER     NOT NULL DEFAULT 0,
    max_selections   INTEGER     NOT NULL DEFAULT 1,
    required         BOOLEAN     NOT NULL DEFAULT FALSE,
    position         INTEGER     NOT NULL DEFAULT 0,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_modifier_group_restaurant_name UNIQUE (restaurant_id, name),
    CONSTRAINT ck_modifier_group_min_max CHECK (min_selections >= 0 AND max_selections >= min_selections)
);

CREATE TABLE IF NOT EXISTS modifiers (
    id               BIGSERIAL PRIMARY KEY,
    restaurant_id    BIGINT      NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    group_id         BIGINT      NOT NULL REFERENCES modifier_groups (id) ON DELETE CASCADE,
    name             VARCHAR(120) NOT NULL,
    price_delta      NUMERIC(12,2) NOT NULL DEFAULT 0,
    position         INTEGER     NOT NULL DEFAULT 0,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_modifier_restaurant_group_name UNIQUE (group_id, name)
);

CREATE TABLE IF NOT EXISTS product_modifier_groups (
    id               BIGSERIAL PRIMARY KEY,
    restaurant_id    BIGINT      NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    product_id       BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    group_id         BIGINT      NOT NULL REFERENCES modifier_groups (id) ON DELETE CASCADE,
    position         INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uq_product_modifier_group UNIQUE (product_id, group_id)
);

CREATE TABLE IF NOT EXISTS order_item_modifiers (
    id               BIGSERIAL PRIMARY KEY,
    restaurant_id    BIGINT      NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    order_item_id    BIGINT      NOT NULL REFERENCES order_items (id) ON DELETE CASCADE,
    group_name       VARCHAR(120) NOT NULL,
    modifier_name    VARCHAR(120) NOT NULL,
    price_delta      NUMERIC(12,2) NOT NULL DEFAULT 0,
    quantity         INTEGER     NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Índices: PK/BIGSERIAL no indexa, y estas son rutas calientes de cada pedido.
CREATE INDEX IF NOT EXISTS idx_modifier_groups_restaurant
    ON modifier_groups (restaurant_id, position);
CREATE INDEX IF NOT EXISTS idx_modifiers_group
    ON modifiers (group_id, position);
CREATE INDEX IF NOT EXISTS idx_product_modifier_groups_product
    ON product_modifier_groups (product_id);
CREATE INDEX IF NOT EXISTS idx_order_item_modifiers_item
    ON order_item_modifiers (order_item_id);
-- Como en el resto del esquema, restaurant_id lidera para podar por tenant.
CREATE INDEX IF NOT EXISTS idx_modifiers_restaurant
    ON modifiers (restaurant_id);
CREATE INDEX IF NOT EXISTS idx_product_modifier_groups_restaurant
    ON product_modifier_groups (restaurant_id);
CREATE INDEX IF NOT EXISTS idx_order_item_modifiers_restaurant
    ON order_item_modifiers (restaurant_id);

COMMENT ON COLUMN modifier_groups.min_selections IS 'Mínimo de opciones elegibles (0 = opcional)';
COMMENT ON COLUMN modifier_groups.max_selections IS 'Máximo de opciones elegibles';
COMMENT ON TABLE order_item_modifiers IS 'Selección del cliente con precio congelado: un pedido viejo debe seguir cuadrando aunque el precio cambie o la opción se borre';