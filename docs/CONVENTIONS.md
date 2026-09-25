# Convenciones

## Nombres

- Services: `<Dominio>Service` con métodos `listMine/getMine/createMine/updateMine/deleteMine`
  (tenant del JWT) más puertas explícitas `<verbo>…(…, Long restaurantId, …)` para
  otros módulos (ej. `findDeliveredBetween`, `slugOrThrow`, `getReferenceById`).
- DTOs: `<Caso>Request` (validado con Bean Validation) y `<Caso>Response`
  (solo lectura, `record`).
- Controllers: un recurso por controller, mismo prefijo REST que el módulo
  (excepción: `ProductStockController` sirve `/api/inventory/*` del stock).

## Dónde va la lógica

- Controller: recibir, validar (`@Valid`) y delegar. Sin `if` de negocio, sin
  streams de ensamblado, sin tocar repositories (regla ArchUnit).
- Service: casos de uso + tenant (`SecurityUtils.currentRestaurantId()`) +
  transacciones. Lo ajeno, vía services, nunca vía repositories.
- Puertas entre módulos: métodos públicos con `restaurantId` explícito y sin
  `SecurityUtils`, para que el llamador controle el tenant.

## Permisos (una convención)

Los permisos por rol/permiso **se declaran en el controller** con
`@PreAuthorize` (`@permissions.has('X')`, `hasRole(...)`); **los services no
repiten el chequeo** y solo validan reglas que dependen de datos
(pertenencia al tenant, auto-eliminación, máquina de estados).
Referencia: `UserController` (`@PreAuthorize("@permissions.has('USERS_MANAGE')"`)
+ `UserService` (solo scoping y reglas de datos).

## Entidades: relaciones

- `User` usa `@ManyToOne` (`restaurant`, `role` en `LAZY` + `fetch join` en
  listados); el resto usa **ids escalares** (`restaurantId`, `categoryId`,
  `productId`, …). Motivo: los agregados se leen/escriben por tenant con
  consultas planas y baratas; las relaciones JPA solo donde la navegación
  es constante (usuario → restaurante/rol). No unificar a la fuerza.

## Repositories con tenant

- Entidades con `restaurant_id` implementan `shared.tenancy.TenantOwned`.
- Sus repositorios solo declaran métodos con `restaurantId`
  (`TenantRepositoriesTest`); los heredados (`findById`, …) solo en `admin`.
- Sin tenant propio (`order_items`, `order_status_history`, `recipe_items`,
  `stored_files`): acceso vía service que valida pertenencia al padre.

## Mapeo entidad <-> DTO

Un solo estilo: método estático `from(...)` **en el DTO**. El service resuelve
URLs firmadas/conteos y el DTO ensambla. Sin `new XxxResponse(...)` fuera del
propio DTO.

## Agregar un módulo nuevo

1. Paquete `com.menusaas.<modulo>` con `controller/dto/entity/repository/service`
   (+ `package-info.java`: responsabilidad y qué expone).
2. Entidad con tenant → implementar `TenantOwned`; repository solo con
   `restaurantId`; controller delgado + `@PreAuthorize`; respuestas con `from`.
3. Puertas para otros módulos como métodos con `restaurantId` explícito.
4. Verificar con `mvn -B verify` (docker para Testcontainers) y, si el módulo
   es cross-tenant como `admin`, documentar la excepción en los tests de
   arquitectura.
