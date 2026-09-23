-- V19: Cobro y cierre de caja.
-- payment_method: cómo se pagó el pedido (solo al cobrar, nullable).
-- cash_closings: un arqueo por restaurante y día (advertencia: la app lo
-- garantiza por lógica; índice único para blindarlo en BD).

ALTER TABLE orders
    ADD COLUMN payment_method VARCHAR(20),
    ADD COLUMN paid_at TIMESTAMPTZ;

CREATE TABLE cash_closings (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    business_date DATE         NOT NULL,
    expected_cash NUMERIC(12,2) NOT NULL DEFAULT 0,
    counted_cash  NUMERIC(12,2) NOT NULL DEFAULT 0,
    difference    NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes         TEXT,
    closed_by     BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_closing_restaurant_day UNIQUE (restaurant_id, business_date)
);

CREATE INDEX idx_closings_restaurant ON cash_closings (restaurant_id, business_date DESC);
CREATE INDEX idx_orders_paid ON orders (restaurant_id, status, paid_at);
