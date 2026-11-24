ALTER TABLE restaurants ADD COLUMN business_zone varchar(64) NOT NULL DEFAULT 'America/Bogota';
ALTER TABLE orders ADD COLUMN request_id uuid, ADD COLUMN request_hash varchar(64),
    ADD COLUMN discounted_by bigint REFERENCES users(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX uq_orders_request ON orders(restaurant_id, request_id) WHERE request_id IS NOT NULL;
CREATE INDEX idx_orders_restaurant_created ON orders(restaurant_id, created_at DESC, id DESC);

-- Preserve every previous count, including the original close on the first correction.
CREATE TABLE cash_closing_revisions (
    id bigserial PRIMARY KEY,
    restaurant_id bigint NOT NULL REFERENCES restaurants(id),
    closing_id bigint NOT NULL REFERENCES cash_closings(id),
    previous_count jsonb NOT NULL,
    reason text NOT NULL,
    corrected_by bigint REFERENCES users(id) ON DELETE SET NULL,
    corrected_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE cash_closing_revisions ENABLE ROW LEVEL SECURITY;
CREATE FUNCTION preserve_cash_closing() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO cash_closing_revisions(restaurant_id, closing_id, previous_count, reason, corrected_by)
    VALUES (OLD.restaurant_id, OLD.id, to_jsonb(OLD), NEW.notes, NEW.closed_by);
    RETURN NEW;
END $$;
CREATE TRIGGER cash_closing_correction BEFORE UPDATE ON cash_closings
FOR EACH ROW EXECUTE FUNCTION preserve_cash_closing();
