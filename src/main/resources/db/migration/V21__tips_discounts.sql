-- V21: Propinas y descuentos por pedido.
-- total = subtotal ítems - discount + tip (calculado en el servicio,
-- nunca confiando en el total que mande el cliente).
ALTER TABLE orders
    ADD COLUMN discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN tip_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
