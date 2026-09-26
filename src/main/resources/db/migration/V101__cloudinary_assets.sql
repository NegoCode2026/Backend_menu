-- V101: Assets de Cloudinary (gestión real y única de imágenes).
-- NOTA: la versión es 101 (no 26) porque el seed demo de dev ocupa la V100
-- y Flyway rechaza migraciones nuevas con versión menor a la máxima aplicada.
-- Las próximas migraciones deben numerarse por encima de 100.
-- Cada subida a Cloudinary deja una fila con public_id (para destroy),
-- url (http, informativa) y secure_url (la que se guarda en
-- products.image_url / restaurants.logo_url).
-- Tabla global sin restaurant_id (igual que stored_files): el orden por
-- restaurante se logra con la carpeta menu_saas/r{restaurantId} en la nube.
-- stored_files queda solo como lectura legacy de fileIds ya guardados.

CREATE TABLE cloudinary_assets (
    id            BIGSERIAL PRIMARY KEY,
    public_id     VARCHAR(255) NOT NULL UNIQUE,
    url           TEXT         NOT NULL,
    secure_url    TEXT         NOT NULL UNIQUE,
    resource_type VARCHAR(20)  NOT NULL DEFAULT 'image',
    format        VARCHAR(20),
    bytes         BIGINT,
    width         INT,
    height        INT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cloudinary_assets_created ON cloudinary_assets (created_at DESC);
