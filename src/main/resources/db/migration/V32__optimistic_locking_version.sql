-- Migration V32: versionado optimista en datos que se editan a la vez
--
-- No había ningún @Version en el proyecto. Con ello, dos admins editando el
-- mismo producto (o la misma mesa) a la vez, el segundo UPDATE machaca al
-- primero sin error, sin log y sin que nadie lo note. Para precios y
-- costes, eso es pérdida de dinero silenciosa.
--
-- Con @Version, el segundo guardado llega con la versión leída y Hibernate lo
-- rechaza con ObjectOptimisticLockingFailureException, que el handler traduce a
-- 409: el segundo usuario recibe "cambió mientras editabas" en vez de perder su
-- cambio.
--
-- DEFAULT 0 para las filas existentes y NOT NULL desde el principio: así el
-- primer UPDATE posterior ya compara versión.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE restaurant_tables
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE ingredients
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN products.version IS 'Versionado optimista: evita que dos ediciones simultaneas se pisen';
COMMENT ON COLUMN restaurant_tables.version IS 'Versionado optimista';
COMMENT ON COLUMN ingredients.version IS 'Versionado optimista: stock y coste no deben pisarse';