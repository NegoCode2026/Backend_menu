-- Migration V26: índices de rendimiento
--
-- Qué cubre cada uno:
--   * orders (restaurant_id, status, created_at DESC): ruta caliente de caja y
--     utilidades -> findByRestaurantIdAndStatusAndCreatedAtBetween.
--   * orders (restaurant_id, created_at DESC, id DESC): listado paginado por
--     fecha; el id desempata pedidos del mismo instante para que la
--     paginación sea estable.
--   * subscriptions (provider_reference): findByProviderReference, que es la
--     comprobación anti-replay del webhook ePayco. Antes era un seq scan en
--     cada pago recibido.
--   * subscriptions (restaurant_id, status, created_at DESC): GET
--     /subscriptions/mine.
--   * order_items (product_id): la FK es ON DELETE SET NULL y no estaba
--     indexada, así que borrar un producto hacía un seq scan sobre order_items
--     (la tabla que más crece y nunca se archiva).
--   * stock_movements (order_id): FK ON DELETE SET NULL, mismo problema.
--   * refresh_tokens (session_expires_at) y (expires_at): la purga diaria
--     barría la tabla entera.
--   * ingredients (restaurant_id, id): findIngredientInRestaurant, que se llama
--     dentro de un bucle al descontar stock de una receta.
--
-- SOBRE CREATE INDEX CONCURRENTLY
-- Se probó CONCURRENTLY y NO es viable dentro de Flyway: el advisory lock de
-- Flyway (por defecto transaccional) deja una transacción abierta mientras
-- toma el lock, y CREATE INDEX CONCURRENTLY espera a que terminen las
-- transacciones que pudieran ver la tabla. Flyway se bloquea a sí mismo y el
-- arranque se queda colgado indefinidamente. Con
-- flyway.postgresql.transactional.lock=false improvedo parcialmente, pero la
-- carrera con la transacción de comprobación de esquema lo dejó intermitente
-- (creó 2 de 9 índices y se colgó en el tercero), así que no es fiable.
--
-- Qué hacer si orders crece mucho (millones de filas) y el deploy bloquea
-- escrituras: ejecutar estos CREATE INDEX CONCURRENTLY a mano ANTES de
-- desplegar, y marcar la migración como aplicada con
--   ./mvnw flyway:mark -Dmigration.version=26
-- A día de hoy el volumen lo hace innecesario: son segundos, no minutos.

CREATE INDEX IF NOT EXISTS idx_orders_restaurant_status_created
    ON orders (restaurant_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_orders_restaurant_created_id
    ON orders (restaurant_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_subscriptions_provider_reference
    ON subscriptions (provider_reference);

CREATE INDEX IF NOT EXISTS idx_subscriptions_restaurant_status_created
    ON subscriptions (restaurant_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_order_items_product_id
    ON order_items (product_id);

CREATE INDEX IF NOT EXISTS idx_stock_movements_order_id
    ON stock_movements (order_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_session_expires
    ON refresh_tokens (session_expires_at);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires
    ON refresh_tokens (expires_at);

CREATE INDEX IF NOT EXISTS idx_ingredients_restaurant_id
    ON ingredients (restaurant_id, id);