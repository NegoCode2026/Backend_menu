-- V2: Datos base — roles y plan del SaaS (necesarios en TODOS los ambientes).
-- Los datos de demostración (restaurante Frito Mix, usuarios demo, etc.) viven
-- en db/dev/V10__demo_seed.sql que SOLO se ejecuta en el perfil de desarrollo.

INSERT INTO roles (name, description) VALUES
    ('SUPER_ADMIN',       'Administrador global de la plataforma'),
    ('RESTAURANT_ADMIN',  'Administrador de un restaurante'),
    ('RESTAURANT_USER',   'Usuario operativo de un restaurante');

INSERT INTO plans (code, name, description, price_monthly, active) VALUES
    ('NEGOCODE', 'Plan NegoCode', 'Acceso total a la plataforma: menú digital ilimitado, código QR, recepción de pedidos en tiempo real y administración completa.', 49900, TRUE);