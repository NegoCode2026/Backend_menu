# Módulo Tables (Mesas) — Backend

> MVP esencial y funcional. Cada mesa tiene un `code` UUID único global que viaja en el QR.
> El backend valida ese `code` para saber la mesa real y el restaurante al que pertenece.
> Los QR los dibuja el **frontend**; el backend solo entrega `code + menuUrl`.

## Modelo

Tabla `restaurant_tables` (`V25__restaurant_tables.sql`):

| Columna | Tipo | Notas |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `restaurant_id` | BIGINT FK → `restaurants(id)` `ON DELETE CASCADE` | Tenant. Relación mesa→restaurante: desde la mesa se accede al restaurante vía `RestaurantService.findActiveByIdOrThrow` (ids escalares por convención, sin `@ManyToOne`). |
| `label` | VARCHAR(30) NOT NULL | Nombre visible: `"Mesa 1"`, `"Terraza 2"`. Único por restaurante. |
| `code` | UUID NOT NULL UNIQUE | Código del QR. Generado en Java (`UUID.randomUUID()`), regenerable. |
| `created_at` / `updated_at` | TIMESTAMPTZ | Automáticos. |

Sin `active` ni `position` (decisión MVP).

Tabla `orders` (misma migración V25):

| Columna | Notas |
|---|---|
| `table_id` BIGINT NULL FK → `restaurant_tables(id)` `ON DELETE SET NULL` | Mesa validada. Al borrar la mesa, el pedido conserva `table_number` como snapshot y `table_id` pasa a NULL. |
| `table_number` VARCHAR(30) (existente) | Snapshot del `label` al momento del pedido. No se valida contra nada; si viene `tableCode`, se sobrescribe con el label real. |

## Formato URL del QR (frontend)

```
{APP_BASE_URL}/menu/{slug}?t={code}
ej: https://mi-app.com/menu/fritomix?t=550e8400-e29b-41d4-a716-446655440000
```

Flujo:
1. Dueño crea mesa → backend devuelve `code`.
2. Frontend pide `GET /api/tables/{id}/qr-info` → recibe `menuUrl` → dibuja QR con su librería.
3. Cliente escanea → frontend lee `?t=` → llama `GET /api/public/tables/resolve?code=` → obtiene `restaurantSlug + tableLabel` → auto-rellena.
4. Pedido envía `tableCode` → backend valida pertenencia al slug.

## Endpoints privados (JWT, tenant del token)

Base: `/api/tables`. Auth: cookie/JWT + CSRF (`X-XSRF-TOKEN`) como el resto de la API.

### GET /api/tables — Listar mis mesas (con QR listo para dibujar)

Cada mesa trae `code` + `menuUrl`: el frontend genera todos los QR directamente del listado, sin llamadas extra.

```bash
curl -b cookies.txt http://localhost:8080/api/tables
# {"success":true,"data":[{"id":5,"restaurantId":1,"label":"Mesa 1",
#   "code":"550e8400-...","restaurantSlug":"fritomix",
#   "menuUrl":"http://localhost:4200/menu/fritomix?t=550e8400-...",
#   "createdAt":"...","updatedAt":"..."}]}
```

Frontend (ej. Angular, un QR por mesa):
```ts
const { data: tables } = await api.get('/api/tables');
for (const t of tables) {
  await QRCode.toCanvas(canvasFor(t.id), t.menuUrl);
  // o con angular-qrcode: <qrcode [qrdata]="t.menuUrl" />
}
// El detalle GET /api/tables/{id}, el POST y el PUT devuelven el mismo objeto.
```

### GET /api/tables/{id} — Detalle

```bash
curl -b cookies.txt http://localhost:8080/api/tables/5
# 200 mismo objeto. 404 si es de otro restaurante (no revela existencia).
```

### POST /api/tables — Crear (permiso SETTINGS_EDIT)

```bash
curl -b cookies.txt -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $XSRF" -X POST http://localhost:8080/api/tables \
  -d '{"label":"Mesa 1"}'
# 201 {"success":true,"message":"Mesa creada","data":{"id":5,...,"code":"550e8400-..."}}
# 409 si ya existe ese label en el restaurante.
```

### PUT /api/tables/{id} — Renombrar (permiso SETTINGS_EDIT)

El `code` no cambia (los QR impresos siguen valiendo). Los pedidos viejos conservan el label anterior.

```bash
curl -b cookies.txt -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $XSRF" -X PUT http://localhost:8080/api/tables/5 \
  -d '{"label":"Mesa VIP"}'
```

### DELETE /api/tables/{id} — Eliminar (permiso SETTINGS_EDIT)

Borrado físico. Pedidos con esa mesa: `table_id → NULL`, `table_number` conserva el nombre.

```bash
curl -b cookies.txt -H "X-XSRF-TOKEN: $XSRF" -X DELETE http://localhost:8080/api/tables/5
```

### POST /api/tables/{id}/regenerate-code — Nuevo código (permiso SETTINGS_EDIT)

Invalida QRs impresos con el código anterior.

```bash
curl -b cookies.txt -H "X-XSRF-TOKEN: $XSRF" \
  -X POST http://localhost:8080/api/tables/5/regenerate-code
# {"data":{...,"code":"nuevo-uuid..."}}
```

### GET /api/tables/{id}/qr-info — Datos para dibujar un QR puntual

Atajo con el mismo `menuUrl` del listado, útil si solo necesitas una mesa (ej. reimprimir una). Para ver todas, usa el listado.

```bash
curl -b cookies.txt http://localhost:8080/api/tables/5/qr-info
# {"data":{"tableId":5,"label":"Mesa 1","code":"550e8400-...",
#   "restaurantSlug":"fritomix",
#   "menuUrl":"http://localhost:4200/menu/fritomix?t=550e8400-..."}}
```

## Endpoints públicos (sin auth)

### GET /api/public/tables/resolve?code={uuid} — Validar QR

```bash
curl "http://localhost:8080/api/public/tables/resolve?code=550e8400-e29b-41d4-a716-446655440000"
# 200 {"data":{"tableId":5,"tableLabel":"Mesa 1","restaurantId":1,
#   "restaurantSlug":"fritomix","restaurantName":"Fritomix",
#   "menuUrl":"http://localhost:4200/menu/fritomix?t=550e8400-..."}}
# 400 si el UUID está malformado. 404 si el code no existe o el restaurante está inactivo.
```

## Pedidos con tableCode

`POST /api/public/orders/{slug}` acepta ahora `tableCode` (UUID, opcional):

```bash
curl -H "Content-Type: application/json" \
  -X POST http://localhost:8080/api/public/orders/fritomix -d '{
    "customerName": "Juan",
    "tableCode": "550e8400-e29b-41d4-a716-446655440000",
    "orderType": "DINE_IN",
    "items": [{"productId": 1, "quantity": 2}]
  }'
# 201 {"data":{"id":12,"orderNumber":"FRIT-0012","tableId":5,"tableNumber":"Mesa 1",...}}
```

Reglas:
- Si `tableCode` viene: debe existir y pertenecer al restaurante del `{slug}`. Si es de otro restaurante → `400 "El código de mesa no pertenece a este restaurante"`. Si no existe → `404 "Mesa no encontrada"`. El `tableNumber` se sobrescribe con el label real.
- Si no viene: funciona como antes (`tableNumber` libre legacy). `DELIVERY`/`TAKEAWAY` normalmente no lo envían.
- Staff (`POST /api/orders`, `PATCH /api/orders/{id}` con `UpdateOrderRequest.tableCode`): misma validación contra su propio restaurante.
- `OrderResponse` ahora incluye `tableId` (null = mesa libre/eliminada) + `tableNumber` snapshot.

## Errores

| Caso | Código |
|---|---|
| Label duplicado en el restaurante | 409 `Ya existe una mesa con ese nombre` |
| Mesa ajena / inexistente (privado) | 404 `Mesa no encontrada` |
| `code` inexistente (público/pedido) | 404 `Mesa no encontrada` |
| `code` de otro restaurante en pedido | 400 `El código de mesa no pertenece a este restaurante` |
| UUID malformado en `?code=` | 400 (validación Spring) |
| Sin permiso de escritura | 403 (requiere `SETTINGS_EDIT` / `RESTAURANT_ADMIN`) |

## Archivos del módulo

```
com.menusaas.tables/
  package-info.java
  entity/RestaurantTable.java
  repository/RestaurantTableRepository.java
  dto/TableRequest.java, TableResponse.java, TableQrInfoResponse.java, TableResolveResponse.java
  service/TableService.java (listMine/getMine/createMine/updateMine/deleteMine/regenerateCodeMine/qrInfoMine/resolveByCode/requireBelongsToRestaurant)
  controller/TableController.java (/api/tables)
  controller/PublicTableController.java (/api/public/tables/resolve)
Cambios en otros módulos (solo puertas, sin tocar repos ajenos):
  restaurants/service/RestaurantService.java (+findActiveByIdOrThrow)
  orders/entity/Order.java (+tableId), orders/dto/* (+tableCode), OrderResponse (+tableId),
  orders/service/OrderService.java (valida tableCode vía TableService)
  tests arquitectura/tenancy (+tables)
```

## Plantilla para futuros módulos (norma docs/modules/)

Cada módulo nuevo deja un `docs/modules/<modulo>.md` con: propósito, tabla/migración, cada endpoint (método+ruta+auth+ejemplo curl+request/response+errores) y archivos. Este archivo es el ejemplo a seguir.
