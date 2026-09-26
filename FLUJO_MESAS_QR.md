# Flujo de Mesas y QRs - Explicación paso a paso

> Backend Menu SaaS - Estado actual del sistema (Septiembre 2026)

---

## 1. ¿Qué es el proyecto?

Es un **Backend para restaurantes** tipo SaaS (Software como Servicio).

Imagina algo como Rappi pero muy simple: cada restaurante se registra, carga sus productos (hamburguesas, pizzas) organizados por categorías, y el sistema le genera un **link** y un **código QR** para que lo ponga en las mesas.

El cliente escanea el QR, ve el menú digital y hace el pedido desde el celular.

**Stack técnico:** Java 21 + Spring Boot 3.5 + PostgreSQL + Flyway + ZXing (QR)

---

## 2. ¿Cómo está organizado el código?

```
Restaurante (ej: Fritomix, slug = "fritomix")
  ├── Categorías (Hamburguesas, Bebidas)
  │     └── Productos (Hamburguesa Especial $18.000)
  ├── Pedidos (Orders)
  └── QR (1 por restaurante)
```

**Archivos clave:**

| Módulo | Archivo | Descripción |
|---|---|---|
| QR | `src/main/java/com/menusaas/qr/controller/QrController.java` | 3 endpoints para descargar QR |
| QR | `src/main/java/com/menusaas/qr/service/QrCodeService.java` | Genera PNG y PDF con ZXing + OpenPDF |
| Mesas (pedidos) | `src/main/java/com/menusaas/orders/entity/Order.java` | Entidad pedido con campo `table_number` |
| Pedidos | `src/main/java/com/menusaas/orders/dto/CreateOrderRequest.java` | DTO que recibe el pedido público |
| Pedidos | `src/main/java/com/menusaas/orders/controller/PublicOrderController.java` | `POST /api/public/orders/{slug}` (público) |
| Pedidos | `src/main/java/com/menusaas/orders/service/OrderService.java` | Lógica de creación y validación |
| Menú público | `src/main/java/com/menusaas/menus/controller/PublicMenuController.java` | `GET /api/public/menu/{slug}` |
| Menú público | `src/main/java/com/menusaas/menus/service/PublicMenuService.java` | Busca menú por slug |
| Restaurante | `src/main/java/com/menusaas/restaurants/entity/Restaurant.java` | Entidad restaurante con `slug` único |
| Config | `src/main/java/com/menusaas/config/AppProperties.java` | `appBaseUrl` para armar la URL del QR |
| DB | `src/main/resources/db/migration/V4__orders.sql` | Define `table_number VARCHAR(30)` |
| DB | `src/main/resources/db/migration/V5__orders_order_number_unique.sql` | Constraint `UNIQUE(restaurant_id, order_number)` |

**No existe:** `Table.java`, `TableRepository`, `tables.sql`, `TableController`. No hay tabla de mesas en la base de datos.

---

## 3. Las "Mesas" hoy NO existen de verdad

Este es el punto más importante.

En la base de datos **no hay una tabla de mesas**. Solo hay una columna de texto libre en la tabla de pedidos:

```sql
-- V4__orders.sql:9
table_number VARCHAR(30)  -- nullable
```

```java
// Order.java:38
@Column(name = "table_number", length = 30)
private String tableNumber;
```@Column(name = "table_number", length = 30)
private String tableNumber;

### ¿Qué significa eso?

Cuando el cliente hace un pedido, manda esto:

```json
{
  "customerName": "Juan",
  "customerPhone": "3001234567",
  "tableNumber": "Mesa 4",
  "notes": "Sin cebolla",
  "items": [{"productId": 1, "quantity": 2}]
}
```El campo `tableNumber` está definido en `CreateOrderRequest.java:19`:


El campo `tableNumber` está definido en `CreateOrderRequest.java:19`:

```java
@Size(max = 30) String tableNumber // opcional, max 30 caracteres
```

El backend **no valida** si la "Mesa 4" existe. Puedes escribir:
- "Mesa 4"
- "mesa 4"
- "terraza"
- "llevar a casa"
- Dejarlo vacío

No hay una lista de mesas del restaurante, no hay estados (ocupada/libre), no hay capacidad ni zona.

Solo se guarda ese texto para:
1. Que el cocinero sepa a dónde llevarlo
2. Mandarlo por WhatsApp: `"Ubicación/Mesa: Mesa 4"` (`WhatsAppNotificationService.java:60`)
3. Mostrarlo en `OrderResponse.java:16`

---

## 4. Los QR hoy son 1 por restaurante, NO 1 por mesa

### Endpoints existentes (`QrController.java:28`)

Todos requieren estar logueado como dueño del restaurante (obtienen el `restaurantId` del JWT):

| Endpoint | Qué hace | Archivo |
|---|---|---|
| `GET /api/qr/url` | Devuelve JSON `{"url": "http://.../menu/fritomix"}` | `QrController.java:58` |
| `GET /api/qr/png` | Descarga imagen PNG del QR | `QrController.java:37` |
| `GET /api/qr/pdf` | Descarga PDF A4 con el QR centrado | `QrController.java:47` |

### ¿Cómo se genera la URL? (`QrController.java:64`)

```java
String base = appProperties.appBaseUrl().replaceAll("/+$", ""); // ej: http://localhost:4200
String url = base + "/menu/" + slug; // ej: http://localhost:4200/menu/fritomix
```

- `appBaseUrl` viene de `AppProperties.java:15` y por defecto es `http://localhost:4200` (variable `APP_BASE_URL`)
- `slug` se obtiene del restaurante del usuario logueado (`SecurityUtils.currentRestaurantId()`)

**El QR nunca apunta a la API**, solo a la web del menú.

### ¿Cómo se dibuja el QR? (`QrCodeService.java:22`)

- **Librería:** ZXing 3.5.3 (`QRCodeWriter`)
- **Tamaño:** 512x512 px (`QR_SIZE = 512`)
- **Corrección de errores:** Nivel M
- **Margen:** 1
- **PDF:** Usa OpenPDF 1.3.30. Genera el PNG en memoria, lo convierte a imagen PDF y lo centra en una hoja A4 de 400x400. Sin logo, sin nombre del restaurante, solo el QR.

```java
// QrCodeService.java:26
BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 512, 512, hints);
MatrixToImageWriter.writeToStream(matrix, "PNG", out);
```

Todos los QR tienen `Cache-Control: max-age=1h`.

### Consecuencia

Es **el mismo QR para todo el restaurante**. Lo imprimes 10 veces y lo pegas en las 10 mesas. Todos los clientes escanean el mismo código y entran a la misma página. Después **el cliente tiene que escribir a mano** "Mesa 5" en un campo de texto.

---

## 5. Flujo completo paso a paso (cómo funciona hoy)

### Paso A - El dueño prepara todo (CON login)

1. **Se registra:** `POST /api/auth/register` -> se crea `Restaurant {name: "Fritomix", slug: "fritomix", active: true, open: true}` (`Restaurant.java:30`)
2. **Crea categorías:** `POST /api/categories` -> ej: "Hamburguesas"
3. **Crea productos:** `POST /api/products` -> ej: "Hamburguesa Especial $18.000" en categoría "Hamburguesas"
4. **Descarga QR:** `GET /api/qr/png` -> obtiene `qr-menu.png` -> lo imprime 10 copias y las pega en las paredes/mesas

### Paso B - El cliente pide (SIN login, es público)

1. **Escanea:** Cliente en la mesa escanea el QR genérico con el celular -> abre `http://tu-app.com/menu/fritomix`
2. **Ve el menú:** El frontend llama a `GET /api/public/menu/fritomix` (`PublicMenuController.java:31`) -> el backend en `PublicMenuService.java:31` busca el restaurante por `slug`, valida que esté `active`, trae categorías activas y productos disponibles, y devuelve todo. Las imágenes vienen con URLs firmadas.
3. **Elige y escribe mesa:** Cliente elige "2x Hamburguesa", escribe **a mano** en un input "Mesa 4" y pulsa "Pedir"
4. **Envía pedido:** El frontend llama a `POST /api/public/orders/fritomix` (`PublicOrderController.java:23`) con el JSON del punto 3. Este endpoint es **público** y no pide CSRF (`SecurityConfig.java:61` lo ignora).

5. **Backend crea el pedido** (`OrderService.java:34` `createPublicOrder`):
   ```java
   1. Busca restaurante por slug con LOCK pesimista (para evitar duplicar números)
      -> findBySlugForUpdate(slug) + filter(isActive)
   2. Verifica que esté abierto -> if (!isOpen()) rechaza: "restaurante cerrado"
   3. Valida cada producto -> findByIdAndRestaurantId + isAvailable
   4. Suma total -> unitPrice * quantity
   5. Genera número de pedido -> FRIT-0001 (4 letras del slug + consecutivo count+1)
      -> generatePrefix(slug) + countOrdersForRestaurant()+1
   6. Guarda Order + items en cascada con tableNumber = "Mesa 4" (trim)
   ```

### Paso C - El dueño gestiona (CON login)

1. **Ve pedidos:** `GET /api/orders` o `GET /api/orders?status=PENDING` (`OrderController.java:26`)
2. **Ve detalle:** `GET /api/orders/{id}` -> solo ve los de su restaurante (si intenta ver de otro, 404)
3. **Cambia estado:** `PATCH /api/orders/1/status {status: "DELIVERED"}` (`OrderController.java:37`) -> si marca como `DELIVERED`, intenta mandar notificación WhatsApp al `customerPhone` con la mesa.
4. **Notifica manual:** `POST /api/orders/{id}/notify-whatsapp`

---

## 6. Resumen del problema y limitaciones

| Aspecto | Estado actual | Limitación |
|---|---|---|
| **Inventario de mesas** | No existe | No puedes listar, crear, editar ni desactivar mesas. No hay capacidad, zona, etc. |
| **QR por mesa** | No existe (1 QR genérico) | No hay "QR Mesa 1", "QR Mesa 2". Todos escanean lo mismo. |
| **Validación de mesa** | Texto libre | Typos ("mesa 4" vs "Mesa 4") generan datos sucios. No hay trazabilidad. |
| **Pre-carga de mesa** | No | La URL no lleva `?table=4`, el cliente debe escribirlo siempre. |
| **Estadísticas por mesa** | Imposible | No puedes saber qué mesa vende más, tiempo por mesa, etc. |
| **PDF del QR** | Solo QR centrado | Sin nombre del restaurante, sin instrucciones, sin logo. |
| **Cobro por mesa** | Manual | No hay cierre de cuenta por mesa. |

---

## 7. ¿Cómo sería con mesas de verdad? (Propuesta futura)

Si se quisiera evolucionar, habría que:

1. **Crear tabla `restaurant_tables`:**
   ```sql
   CREATE TABLE restaurant_tables (
     id BIGSERIAL PRIMARY KEY,
     restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
     label VARCHAR(30) NOT NULL, -- "Mesa 1", "Terraza 2"
     qr_token VARCHAR(64) UNIQUE, -- para URL firmada
     active BOOLEAN DEFAULT TRUE,
     position INT,
     UNIQUE(restaurant_id, label)
   );
   ```

2. **CRUD de mesas:** `GET/POST/PUT/DELETE /api/tables` (tenant-scoped)

3. **QR por mesa:**
   ```
   GET /api/qr/tables/{id}/png -> genera QR con URL:
   http://app.com/menu/fritomix?table=Mesa+4&t=abc123
   ```

4. **Menú público** lee `?table=Mesa+4` y auto-rellena el campo (y lo bloquea si viene del QR).

5. **Crear pedido** valida: `existsByRestaurantIdAndLabel(tableNumber)` -> rechaza si la mesa no existe.

---

*Archivo generado automáticamente - Backend_menu - Septiembre 2026*
