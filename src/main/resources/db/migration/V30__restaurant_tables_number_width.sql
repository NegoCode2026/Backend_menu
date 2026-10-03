-- Migration V30: restaurant_tables.number a VARCHAR(20)
--
-- Por qué existe una migración separada y no está en V28:
-- V28 ya estaba aplicada cuando se le añadió este bloque, así que Flyway lo
-- tiene por ejecutada y solo realinea el checksum (flyway.repair) sin volver a
-- correrla. Editar una migración ya aplicada no ejecuta nada: es precisamente
-- el patrón que causesó el 500 original y que este proyecto dejó de hacer.
-- Regla: una migración aplicada es inmutable; los cambios van en una nueva.
--
-- Al renombrar table_number -> number en V28, la columna conservó su VARCHAR(50)
-- de origen, mientras V25 declara VARCHAR(20). No rompe el código (la entidad
-- pide length=20 y 50 la acepta), pero hace que el esquema de esta base difiera
-- del que produce una instalación limpia.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'restaurant_tables' AND column_name = 'number'
                 AND character_maximum_length <> 20) THEN
        ALTER TABLE restaurant_tables ALTER COLUMN number TYPE VARCHAR(20);
        RAISE NOTICE 'restaurant_tables.number ajustada a VARCHAR(20)';
    END IF;
END $$;