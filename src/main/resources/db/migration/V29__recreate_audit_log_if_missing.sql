-- Migration V29: recrear audit_log si falta
--
-- Hallazgo al auditar el esquema: esta base tiene V10 ("audit log") registrada
-- en flyway_schema_history como aplicada con éxito, pero la tabla audit_log NO
-- existe:
--   ERROR: relation "audit_log" does not exist
--
-- Por qué nadie lo notó:
--   * flyway.repair() no re-ejecuta migraciones ya aplicadas, así que aunque
--     V10 constara como aplicada nunca volvió a crearse. La tabla debe haber
--     desaparecido después del 2026-09-12 (queda registrada esa fecha).
--   * AuditService.log() es best-effort y captura la excepción: las escrituras
--     de auditoría se perdían en silencio, con un simple WARN.
--   * AuditService.list() NO captura excepciones, así que GET /api/admin/audit
--     devolvía 500.
--   * ddl-auto=none en prod: nada validaba el esquema al arrancar.
--
-- Es condicional porque en una base creada desde estas migraciones la tabla ya
-- existe y no hay que hacer nada.

DO $$
BEGIN
    IF to_regclass('public.audit_log') IS NULL THEN
        RAISE NOTICE 'audit_log no existe: recreando';
        EXECUTE '
            CREATE TABLE IF NOT EXISTS audit_log (
                id          BIGSERIAL PRIMARY KEY,
                actor_id    BIGINT REFERENCES users (id) ON DELETE SET NULL,
                actor_email VARCHAR(160),
                action      VARCHAR(80) NOT NULL,
                entity_type VARCHAR(50) NOT NULL,
                entity_id   BIGINT,
                detail      TEXT,
                created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log (entity_type, entity_id)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log (created_at DESC)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_audit_log_actor ON audit_log (actor_id)';
    END IF;
END $$;

-- V7 habilitó RLS en las tablas que existían en ese momento. audit_log se creó
-- después (V10), así que nunca la recibió. No es una barrera real aquí porque
-- el backend entra como propietario y las policies están vacías a propósito,
-- pero dejarlo alineado evita confundir a quien lea el esquema.
DO $$
BEGIN
    IF to_regclass('public.audit_log') IS NOT NULL THEN
        EXECUTE 'ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY';
    END IF;
END $$;