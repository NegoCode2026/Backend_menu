-- V23: Costo unitario de compra del ingrediente.
-- Permite calcular el costo del plato sumando sus ingredientes
-- (ej. 2 huevos x 600 + 1 salchicha x 1000 = 2200 de costo).
ALTER TABLE ingredients
    ADD COLUMN unit_cost NUMERIC(12,2) NOT NULL DEFAULT 0;
