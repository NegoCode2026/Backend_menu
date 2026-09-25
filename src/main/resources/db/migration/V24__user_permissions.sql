-- V24: Permisos asignables a una PERSONA concreta, además de los del rol.
-- Sin filas = la persona hereda los permisos de su rol (comportamiento actual).
-- Con filas = la persona tiene exactamente esos permisos (aunque su rol dé más).
-- El rol RESTAURANT_ADMIN siempre tiene todo (blindado en código, no en BD).
CREATE TABLE user_permissions (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT      NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission    VARCHAR(50) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_permission UNIQUE (restaurant_id, user_id, permission)
);

CREATE INDEX idx_user_permissions_lookup ON user_permissions (restaurant_id, user_id);
