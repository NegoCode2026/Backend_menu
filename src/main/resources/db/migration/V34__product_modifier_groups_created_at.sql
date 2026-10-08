-- Migration V34: created_at en product_modifier_groups
--
-- V33 se aplicó sin esta columna en product_modifier_groups, mientras la entidad
-- ProductModifierGroup sí la mapeaba (@CreationTimestamp). Resultado: 500 al
-- leer los grupos de un producto, con "column pmg1_0.created_at does not exist".
--
-- Va en una migración nueva y no editando V33 a propósito: una migración ya
-- aplicada es inmutable, y editarla no ejecutaría el cambio (lo documentó el
-- incidente de V28, y lo comprueba el job `migrations` de la CI).

ALTER TABLE product_modifier_groups
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();