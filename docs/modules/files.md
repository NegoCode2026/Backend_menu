# Módulo Files (Imágenes) — Backend

> Gestión real y única de imágenes en **Cloudinary**. Cada subida persiste un
> `CloudinaryAsset` con `public_id`, `url` y `secure_url`. Sin fallback a BD:
> sin credenciales o ante un fallo, la subida responde 503/400.

## Modelo

Tabla `cloudinary_assets` (`V26__cloudinary_assets.sql`), global sin tenant:

| Columna | Notas |
|---|---|
| `id` | PK |
| `public_id` UNIQUE | Identificador en Cloudinary (carpeta `menu_saas/r{restaurantId}/...`). Necesario para `destroy`. |
| `url` TEXT | http (informativa) |
| `secure_url` TEXT UNIQUE | https. **Es el valor que se guarda** en `products.image_url` / `restaurants.logo_url`. |
| `resource_type`, `format`, `bytes`, `width`, `height` | Metadatos del upload |
| `created_at` | Automática |

`stored_files` (V11) queda solo como **lectura legacy** de fileIds ya guardados; ya no se escribe por el flujo normal.

## Configuración (credenciales reales)

Requeridas en el entorno (dev y prod):

```bash
CLOUDINARY_CLOUD_NAME=tu-cloud
CLOUDINARY_API_KEY=123456789
CLOUDINARY_API_SECRET=secreto
# o, alternativamente:
CLOUDINARY_URL=cloudinary://API_KEY:API_SECRET@CLOUD_NAME
```

Sin ninguna de ellas el bean `Cloudinary` no existe y `POST /api/files/upload`
responde `503 SERVICE_UNAVAILABLE`. El resto de la API sigue funcionando
(las imágenes ya guardadas se siguen sirviendo).

## Endpoints

### POST /api/files/upload — Subir imagen (JWT + permiso `MENU_EDIT` + CSRF)

```bash
curl -b cookies.txt -H "X-XSRF-TOKEN: $XSRF" \
  -F "file=@hamburguesa.png" http://localhost:8080/api/files/upload
# 201 {"success":true,"message":"Imagen subida","data":{
#   "url":"https://res.cloudinary.com/.../menu_saas/r1/hamburguesa.png",
#   "fileId":"https://res.cloudinary.com/.../menu_saas/r1/hamburguesa.png",
#   "publicId":"menu_saas/r1/hamburguesa"}}
```

- Valida tipo declarado: JPG, PNG, WEBP, GIF, SVG (otro → `400`).
- Límite 5 MB/archivo (Spring multipart → `413` si se supera).
- `url` y `fileId` son la `secure_url`. Guárdala tal cual en `imageUrl`/`logoUrl` al crear/editar producto o restaurante; `SignedUrlService` deja pasar `https://` sin firmar.
- Fallo de red/Cloudinary → `400`. Sin credenciales → `503`.

### GET /api/public/files/{fileId}?exp=&sig= — Lectura legacy (pública, con firma)

Solo para fileIds viejos (`UUID.png`) guardados antes de Cloudinary. Sin cambios:
firma HMAC + expiración (default 1 h), `400` si es inválida/expirada.

## Ciclo de vida (destroy)

Al **reemplazar** la imagen de un producto (`PUT /api/products/{id}` con otra
`imageUrl`), el **logo** (`PUT /api/restaurants/me`), o al **eliminar** un
producto (`DELETE /api/products/{id}`, incluido el borrado en cascada por
categoría), el backend destruye el asset anterior en Cloudinary **solo si**
ningún otro producto del restaurante (o ningún otro restaurante, para logos)
lo referencia. URLs externas (Unsplash, etc.), Data-URI y fileIds legacy
nunca se tocan. Un fallo del `destroy` solo se loguea (no rompe el pedido).

Caveat MVP: el conteo es por módulo (productos ↔ productos, logos ↔ logos).
Si la misma URL se usa como imagen de producto **y** como logo, reemplazar
un lado puede invalidar el otro. No compartir URLs entre módulos.

## Errores

| Caso | Código |
|---|---|
| Tipo no permitido / vacío | 400 `Formato de imagen no soportado...` |
| Cloudinary no configurado | 503 `Almacenamiento de imágenes no configurado...` |
| Fallo de subida | 400 `No se pudo subir la imagen...` |
| Archivo > 5 MB | 413 `FILE_TOO_LARGE` |
| Sin permiso | 403 (requiere `MENU_EDIT`) |
| Firma legacy inválida/expirada | 400 `Enlace de imagen inválido o expirado` |

## Archivos del módulo

```
com.menusaas.files/
  package-info.java
  config/CloudinaryConfig.java (bean Cloudinary solo si hay credenciales)
  entity/CloudinaryAsset.java + repository/CloudinaryAssetRepository.java
  service/CloudinaryAssetService.java (upload/destroyBySecureUrlIfOwned)
  service/CloudinaryFileStorageService.java (@Primary: subida Cloudinary, lectura legacy en BD)
  service/DatabaseFileStorageService.java + LocalFileStorageService.java (solo lectura legacy)
  controller/FileController.java (POST /api/files/upload)
  controller/PublicFileController.java (GET /api/public/files/... legacy)
Cambios en otros módulos (puertas, sin tocar repos ajenos):
  products/service/ProductService.java (destroy al reemplazar/eliminar)
  products/repository/ProductRepository.java (+existsByImageUrlAndRestaurantId[AndIdNot])
  restaurants/service/RestaurantService.java (destroy al reemplazar logo)
  restaurants/repository/RestaurantRepository.java (+existsByLogoUrlAndIdNot)
  shared/api/ServiceUnavailableException.java (+handler 503)
```
