-- V18: La categoría pasa a ser opcional. Un producto puede existir sin
-- categoría (se muestra en "Sin categoría" en el menú público).
-- No se borra la tabla categories: los datos existentes siguen intactos.
ALTER TABLE products ALTER COLUMN category_id DROP NOT NULL;
