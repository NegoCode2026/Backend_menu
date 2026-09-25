-- V20: Permisos asignables por rol y restaurante.
-- Si un rol NO tiene filas, valen los defaults del código; al personalizar
-- por primera vez se copian los defaults y se editan libremente.
-- RESTAURANT_ADMIN siempre tiene todo (blindado en código, no en BD).
CREATE TABLE role_permissions (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    role          VARCHAR(50)  NOT NULL,
    permission    VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_role_permission UNIQUE (restaurant_id, role, permission)
);

CREATE INDEX idx_role_permissions_lookup ON role_permissions (restaurant_id, role);
