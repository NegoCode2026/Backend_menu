-- V13: Tipo de pedido (mesa, domicilio, para llevar) y marcadores de tiempo
-- por estado (listo / entregado) para el módulo de pedidos.

ALTER TABLE orders
    ADD COLUMN order_type   VARCHAR(20)  NOT NULL DEFAULT 'DINE_IN',
    ADD COLUMN ready_at     TIMESTAMPTZ,
    ADD COLUMN delivered_at TIMESTAMPTZ;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_order_type
        CHECK (order_type IN ('DINE_IN', 'DELIVERY', 'TAKEAWAY'));