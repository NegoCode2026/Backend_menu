-- Migration V12: Cambiar columnas image_url y logo_url a TEXT para almacenamiento directo de cadenas Base64 (Data URI)
ALTER TABLE products ALTER COLUMN image_url TYPE TEXT;
ALTER TABLE restaurants ALTER COLUMN logo_url TYPE TEXT;
