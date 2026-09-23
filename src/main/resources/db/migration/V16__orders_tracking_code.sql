-- Código de seguimiento único por pedido (UUID generado al crearlo desde el
-- menú público). Permite al cliente consultar el estado de su pedido sin
-- autenticación y sin exponer pedidos de otros clientes (no enumerable).
ALTER TABLE orders ADD COLUMN tracking_code VARCHAR(36);
CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_tracking_code ON orders(tracking_code);