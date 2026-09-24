-- V22: Inventario por ingredientes + recetas por plato.
-- El menú vende PLATOS; el stock vive en INGREDIENTES. Al pedir un plato
-- con receta se descuentan sus ingredientes; sin receta se usa el stock
-- propio del producto (comportamiento anterior, compatible).
CREATE TABLE ingredients (
    id                   BIGSERIAL PRIMARY KEY,
    restaurant_id        BIGINT        NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    name                 VARCHAR(160)  NOT NULL,
    unit                 VARCHAR(20)   NOT NULL DEFAULT 'und',
    stock_quantity       NUMERIC(12,2) NOT NULL DEFAULT 0,
    low_stock_threshold  NUMERIC(12,2) NOT NULL DEFAULT 5,
    track_stock          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ingredients_restaurant ON ingredients (restaurant_id, name);

CREATE TABLE recipe_items (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    ingredient_id BIGINT        NOT NULL REFERENCES ingredients (id) ON DELETE CASCADE,
    quantity      NUMERIC(12,2) NOT NULL,
    CONSTRAINT uq_recipe_product_ingredient UNIQUE (product_id, ingredient_id),
    CONSTRAINT chk_recipe_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_recipe_product ON recipe_items (product_id);

-- El kardex ahora puede mover ingredientes (product_id pasa a nullable)
-- y cantidades con decimales (gramos, ml).
ALTER TABLE stock_movements
    ADD COLUMN ingredient_id BIGINT REFERENCES ingredients (id) ON DELETE CASCADE,
    ALTER COLUMN product_id DROP NOT NULL,
    ALTER COLUMN quantity TYPE NUMERIC(12,2) USING quantity::NUMERIC(12,2);

CREATE INDEX idx_stock_movements_ingredient ON stock_movements (ingredient_id, created_at DESC);
