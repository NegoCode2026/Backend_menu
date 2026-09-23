-- V11: Almacenamiento de imágenes en base de datos PostgreSQL (BYTEA).
-- Garantiza la persistencia permanente de imágenes a través de reinicios y redeploys de contenedores efímeros (Render, Docker).

CREATE TABLE IF NOT EXISTS stored_files (
    file_id      VARCHAR(120) PRIMARY KEY,
    content_type VARCHAR(100) NOT NULL,
    data         BYTEA        NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stored_files_created ON stored_files (created_at DESC);
