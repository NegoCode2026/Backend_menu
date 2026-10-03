-- Migration V28: alinear restaurant_tables.number con la entidad JPA
--
-- La entidad RestaurantTable mapea la columna como "number", y toda la
-- aplicación (DTOs, TableService, TableResponse) usa "number". Pero las bases
-- de datos que se construyeron en su día quedaron con la columna "table_number":
-- /api/tables devolvía 500 con
--   ERROR: column rt1_0.number does not exist
--
-- Por qué no lo detectaron antes:
--   1. V25 se editó DESPUÉS de aplicarse (deriva de checksum) y el
--      flyway.repair() que corría en cada arranque aceptaba la deriva en
--      silencio, realineando el checksum en vez de señalar el problema.
--   2. El Dockerfile fuerza SPRING_PROFILES_ACTIVE=prod, y en application-prod
--      ddl-auto es "none": en docker y en producción NO hay validación de
--      esquema. Por eso el fallo llegó a runtime como 500 y no al arrancar.
--
-- Es condicional a propósito: en una base creada desde estas migraciones la
-- columna ya se llama "number" y el bloque no hace nada.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'restaurant_tables' AND column_name = 'table_number')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_name = 'restaurant_tables' AND column_name = 'number') THEN
        ALTER TABLE restaurant_tables RENAME COLUMN table_number TO number;
        RAISE NOTICE 'restaurant_tables.table_number renombrada a number';
    END IF;
END $$;

-- Nombres de la restricción y del índice alineados con V25, para que una base
-- creada desde cero y una reparada queden con el mismo esquema.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_restaurant_tables_number') THEN
        ALTER TABLE restaurant_tables RENAME CONSTRAINT uk_restaurant_tables_number TO uq_restaurant_table_number;
    END IF;
END $$;

DROP INDEX IF EXISTS idx_restaurant_tables_restaurant;
DROP INDEX IF EXISTS idx_restaurant_tables_lookup;
-- La columna quedó como VARCHAR(50) (venía de table_number) mientras V25 declara
-- VARCHAR(20). No rompe nada por sí solo, pero hace que el esquema difiera del
-- que produce una instalación limpia. Se ajusta al ancho declarado.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'restaurant_tables' AND column_name = 'number'
                 AND character_maximum_length <> 20) THEN
        ALTER TABLE restaurant_tables ALTER COLUMN number TYPE VARCHAR(20);
    END IF;
END $$;
