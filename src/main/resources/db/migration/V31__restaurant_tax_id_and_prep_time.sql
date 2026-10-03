-- Migration V31: tax_id y estimated_prep_time en restaurants
--
-- El formulario de restaurante ya recogía estos dos campos y los enviaba en
-- PUT /api/restaurants/me, pero no existía ni la columna ni el campo en
-- RestaurantRequest: se aceptaban y se descartaban en silencio. El usuario veía
-- "Restaurante actualizado" y el dato nunca se guardaba. El tiempo estimado de
-- preparación que ve el cliente era siempre el literal '20-30 min', y la
-- factura salía sin NIT.
--
-- Ambos quedan nullable: el restaurante puede no querer rellenarlos.

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS tax_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS estimated_prep_time VARCHAR(60);

COMMENT ON COLUMN restaurants.tax_id IS 'NIT / identificación tributaria del restaurante';
COMMENT ON COLUMN restaurants.estimated_prep_time IS 'Tiempo estimado de preparación, p. ej. 20-30 min';