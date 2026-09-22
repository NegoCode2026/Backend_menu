-- V14: Historial de cambios de estado de pedidos. Cada transición registra
-- el estado anterior, el nuevo y el momento; permite la linea de tiempo
-- (timeline) de un pedido y auditoría.

CREATE TABLE order_status_history (
    id          BIGSERIAL   PRIMARY KEY,
    order_id    BIGINT      NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_status_history_order ON order_status_history (order_id, changed_at);