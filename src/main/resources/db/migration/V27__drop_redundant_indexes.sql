-- Migration V27: limpieza de índices redundantes
--
-- Un CREATE UNIQUE / una restricción UNIQUE ya crea un índice B-tree. Cuando un
-- CREATE INDEX posterior repite esas mismas columnas (o un prefijo exacto), es
-- código muerto: el planificador nunca lo elige porque el otro índice resuelve
-- la misma consulta con la misma o mejor salida. No aporta nada y sí cuesta
-- escritura en cada INSERT/UPDATE.
--
-- Verificado que cada uno es prefijo exacto del índice de la restricción:
--   idx_orders_restaurant_status        ⊂ idx_orders_paid (restaurant_id, status, paid_at)   V19
--   idx_users_email                     ⊂ users_email_key (UNIQUE en V1)
--   idx_categories_restaurant           ⊂ uq_categories_restaurant_name                      V1
--   idx_recipe_product                  ⊂ uq_recipe_product_ingredient                       V22
--   idx_role_permissions_lookup          ⊂ uq_role_permission                                 V20
--   idx_user_permissions_lookup          ⊂ uq_user_permission                                 V24
--
-- restaurant_tables aparece con los dos nombres posibles: idx_restaurant_tables_lookup en
-- bases creadas desde V25, idx_restaurant_tables_restaurant en las construidas antes de
-- esa versión. Ambos son prefijo de la restricción UNIQUE (restaurant_id, number), así que
-- se eliminan los dos. V28 renombra además la columna number de esa tabla.
--
-- NO se toca idx_products_restaurant ni idx_orders_created: son prefijos de
-- índices más anchos, pero también sirven para index-only scan en consultas que
-- solo filtran por restaurant_id. Su eliminación requiere medir con el
-- planner sobre datos reales, no conviene a ciegas.

DROP INDEX IF EXISTS idx_orders_restaurant_status;
DROP INDEX IF EXISTS idx_users_email;
DROP INDEX IF EXISTS idx_categories_restaurant;
DROP INDEX IF EXISTS idx_recipe_product;
DROP INDEX IF EXISTS idx_role_permissions_lookup;
DROP INDEX IF EXISTS idx_user_permissions_lookup;