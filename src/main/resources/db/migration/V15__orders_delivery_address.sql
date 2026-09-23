-- Dirección del domicilio para pedidos de tipo DELIVERY.
-- El cliente la ingresa desde el menú público; el restaurante también puede
-- registrarla al crear o editar un pedido manual.
ALTER TABLE orders ADD COLUMN delivery_address VARCHAR(255);