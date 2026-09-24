# Backend — API Menu SaaS

Plataforma SaaS de menús digitales para restaurantes: catálogo (categorías/productos),
pedidos (staff + públicos por slug), inventario (ingredientes/recetas/kardex), caja,
reportes de utilidad, suscripciones (ePayco/manual), archivos, QR, menú público,
realtime por WebSocket y backoffice multi-tenant.

Monolito modular (Java 21, Spring Boot 3.5, Maven): `com.menusaas.<modulo>` con
`controller/dto/entity/repository/service`. Detalle en `docs/ARCHITECTURE.md` y
convenciones en `docs/CONVENTIONS.md`.

## Levantar en local

```bash
# 1) Copiar variables (nunca commitear el .env)
cp .env.example .env   # completar JWT_SECRET con: openssl rand -base64 64

# 2a) Con Docker (Postgres + backend)
docker compose up --build

# 2b) Sin Docker: Postgres local en 5433 y backend con Maven
./mvnw spring-boot:run
```

- API: http://localhost:8080
- Swagger UI (solo dev): http://localhost:8080/swagger-ui.html
- Salud: http://localhost:8080/actuator/health

## Tests

```bash
./mvnw verify   # unitarios + integración (Testcontainers requiere Docker) + JaCoCo
```

Reglas de arquitectura (`ModuleBoundariesTest`, `TenantRepositoriesTest`) y
aislamiento por tenant (`TenantIsolationIT`) corren dentro del mismo `verify`.

## Variables de entorno (solo nombres)

Base de datos: `DB_URL`, `DB_USER`, `DB_PASSWORD`.
JWT/sesión: `JWT_SECRET` (obligatorio), `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL_HOURS`.
Web/seguridad: `CORS_ALLOWED_ORIGINS`, `APP_BASE_URL`, `API_BASE_URL`, `UPLOAD_DIR`,
`COOKIES_SECURE`, `COOKIE_SAMESITE`, `SIGNED_URL_TTL`, `SESSION_ABSOLUTE_TTL_HOURS`,
`AUTH_RATE_LIMIT_PER_MINUTE`, `PORT`, `SPRING_PROFILES_ACTIVE`.
Pagos: `EPAYCO_PUBLIC_KEY`, `EPAYCO_PRIVATE_KEY`, `EPAYCO_CUSTOMER_ID`, `EPAYCO_P_KEY`.
Archivos: `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `CLOUDINARY_URL`.

Ver valores de ejemplo y cómo generar secretos en `.env.example`.
