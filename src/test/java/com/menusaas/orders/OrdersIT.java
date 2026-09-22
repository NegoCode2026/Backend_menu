package com.menusaas.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ciclo de vida completo de pedidos: creación desde el menú público (sin
 * autenticación), validaciones (producto inexistente / no disponible / slug
 * desconocido / items vacíos), gestión por el restaurante (listado por estado,
 * detalle, cambio de estado) y aislamiento entre tenants.
 *
 * El restaurante demo "fritomix" (restaurant 1, products 1-8) lo siembra Flyway.
 */
class OrdersIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void publicOrder_validations_andLifecycle() throws Exception {
        // Pedido público válido contra el restaurante demo
        ResponseEntity<JsonNode> created = postPublicOrder("fritomix", Map.of(
                "customerName", "Cliente Demo",
                "customerPhone", "3001234567",
                "tableNumber", "Mesa 4",
                "notes", "Sin cebolla",
                "items", List.of(
                        Map.of("productId", 1, "quantity", 2, "notes", "Salsa extra"),
                        Map.of("productId", 4, "quantity", 1)
                )
        ));
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        JsonNode data = created.getBody().get("data");
        assertThat(data.get("orderNumber").asText()).startsWith("FRIT-");
        assertThat(data.get("status").asText()).isEqualTo("PENDING");
        assertThat(data.get("orderType").asText()).isEqualTo("DINE_IN");
        assertThat(data.get("totalAmount").asDouble()).isEqualTo(62000.0);
        assertThat(data.get("items")).hasSize(2);
        assertThat(data.get("items").get(0).get("productName").asText()).isEqualTo("Hamburguesa Especial");
        assertThat(data.get("items").get(0).get("subtotal").asDouble()).isEqualTo(36000.0);
        assertThat(data.get("customerName").asText()).isEqualTo("Cliente Demo");
        assertThat(data.get("timeline")).hasSize(1);
        assertThat(data.get("timeline").get(0).get("toStatus").asText()).isEqualTo("PENDING");

        // Producto inexistente → 400
        ResponseEntity<JsonNode> badProduct = postPublicOrder("fritomix", Map.of(
                "customerName", "C",
                "items", List.of(Map.of("productId", 99999, "quantity", 1))
        ));
        assertThat(badProduct.getStatusCode().value()).isEqualTo(400);

        // Slug desconocido → 404
        ResponseEntity<JsonNode> badSlug = postPublicOrder("no-existe", Map.of(
                "customerName", "C",
                "items", List.of(Map.of("productId", 1, "quantity", 1))
        ));
        assertThat(badSlug.getStatusCode().value()).isEqualTo(404);

        // Items vacíos → 400 (validación)
        ResponseEntity<JsonNode> emptyItems = postPublicOrder("fritomix", Map.of(
                "customerName", "C",
                "items", List.of()
        ));
        assertThat(emptyItems.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void unavailableProduct_isRejected() throws Exception {
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Orders User", "orders-user@test.com", "orders-user");

        // Crear categoría y producto, luego marcarlo como NO disponible
        ResponseEntity<JsonNode> category = rest.exchange("/api/categories", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("name", "Papas", "position", 1, "active", true), session),
                JsonNode.class);
        long categoryId = category.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> product = rest.exchange("/api/products", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of(
                        "categoryId", categoryId,
                        "name", "Papas Francesas",
                        "price", 8000,
                        "available", true,
                        "position", 1), session),
                JsonNode.class);
        long productId = product.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> unavailable = rest.exchange("/api/products/" + productId, HttpMethod.PUT,
                TestHttp.body(objectMapper, Map.of(
                        "categoryId", categoryId,
                        "name", "Papas Francesas",
                        "price", 8000,
                        "available", false), session),
                JsonNode.class);
        assertThat(unavailable.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> order = postPublicOrder("orders-user", Map.of(
                "customerName", "C",
                "items", List.of(Map.of("productId", productId, "quantity", 1))
        ));
        assertThat(order.getStatusCode().value()).isEqualTo(400);
        assertThat(order.getBody().toString()).contains("no se encuentra disponible");
    }

    @Test
    void tenantOrderManagement_isolationAndStatusTransitions() throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Orders Owner", "orders-owner@test.com", "orders-owner");
        TestHttp.Session other = TestHttp.register(rest, objectMapper,
                "Orders Other", "orders-other@test.com", "orders-other");

        // Crear producto propio y recibir un pedido público sobre él
        ResponseEntity<JsonNode> category = rest.exchange("/api/categories", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("name", "Bebidas", "position", 1, "active", true), owner),
                JsonNode.class);
        long categoryId = category.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> product = rest.exchange("/api/products", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of(
                        "categoryId", categoryId,
                        "name", "Limonada",
                        "price", 5000,
                        "available", true), owner),
                JsonNode.class);
        long productId = product.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> placed = postPublicOrder("orders-owner", Map.of(
                "customerName", "Cliente",
                "items", List.of(Map.of("productId", productId, "quantity", 3))
        ));
        assertThat(placed.getStatusCode().value()).isEqualTo(201);
        long orderId = placed.getBody().get("data").get("id").asLong();
        assertThat(placed.getBody().get("data").get("totalAmount").asDouble()).isEqualTo(15000.0);

        // Listado sin filtro y filtrado por estado
        ResponseEntity<JsonNode> list = rest.exchange("/api/orders", HttpMethod.GET, owner.get(), JsonNode.class);
        assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(list.getBody().get("data")).hasSize(1);

        ResponseEntity<JsonNode> filtered = rest.exchange("/api/orders?status=PENDING", HttpMethod.GET,
                owner.get(), JsonNode.class);
        assertThat(filtered.getBody().get("data")).hasSize(1);

        ResponseEntity<JsonNode> filteredNone = rest.exchange("/api/orders?status=DELIVERED", HttpMethod.GET,
                owner.get(), JsonNode.class);
        assertThat(filteredNone.getBody().get("data")).hasSize(0);

        // Detalle propio → 200; ajeno → 404; inexistente → 404
        ResponseEntity<JsonNode> mine = rest.exchange("/api/orders/" + orderId, HttpMethod.GET,
                owner.get(), JsonNode.class);
        assertThat(mine.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(mine.getBody().get("data").get("id").asLong()).isEqualTo(orderId);

        ResponseEntity<JsonNode> others = rest.exchange("/api/orders/" + orderId, HttpMethod.GET,
                other.get(), JsonNode.class);
        assertThat(others.getStatusCode().value()).isEqualTo(404);

        ResponseEntity<JsonNode> missing = rest.exchange("/api/orders/99999", HttpMethod.GET,
                owner.get(), JsonNode.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);

        // Máquina de transiciones: PENDING → CONFIRMED → IN_PREPARATION → READY → DELIVERED.
        // Saltos inválidos (PENDING → DELIVERED) y modificaciones de estados terminales → 400.
        ResponseEntity<JsonNode> confirmed = rest.exchange("/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "CONFIRMED"), owner), JsonNode.class);
        assertThat(confirmed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(confirmed.getBody().get("data").get("status").asText()).isEqualTo("CONFIRMED");

        ResponseEntity<JsonNode> skip = rest.exchange("/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "DELIVERED"), owner), JsonNode.class);
        assertThat(skip.getStatusCode().value()).isEqualTo(400);

        ResponseEntity<JsonNode> inPreparation = rest.exchange("/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "IN_PREPARATION"), owner), JsonNode.class);
        assertThat(inPreparation.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> ready = rest.exchange("/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "READY"), owner), JsonNode.class);
        assertThat(ready.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(ready.getBody().get("data").get("readyAt").isNull()).isFalse();

        ResponseEntity<JsonNode> delivered = rest.exchange("/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "DELIVERED"), owner), JsonNode.class);
        assertThat(delivered.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(delivered.getBody().get("data").get("status").asText()).isEqualTo("DELIVERED");
        assertThat(delivered.getBody().get("data").get("deliveredAt").isNull()).isFalse();
        assertThat(delivered.getBody().get("data").get("timeline")).hasSize(5);

        ResponseEntity<JsonNode> reDelivered = rest.exchange("/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "PENDING"), owner), JsonNode.class);
        assertThat(reDelivered.getStatusCode().value()).isEqualTo(400);

        // Cambiar estado de pedido ajeno → 404
        ResponseEntity<JsonNode> statusOthers = rest.exchange("/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "CANCELLED"), other), JsonNode.class);
        assertThat(statusOthers.getStatusCode().value()).isEqualTo(404);

        // Pedido cancelado ya no se puede modificar
        ResponseEntity<JsonNode> secondOrder = postPublicOrder("orders-owner", Map.of(
                "customerName", "Cliente 2",
                "items", List.of(Map.of("productId", productId, "quantity", 1))
        ));
        long secondId = secondOrder.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> cancelled = rest.exchange("/api/orders/" + secondId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "CANCELLED"), owner), JsonNode.class);
        assertThat(cancelled.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> afterCancel = rest.exchange("/api/orders/" + secondId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "PENDING"), owner), JsonNode.class);
        assertThat(afterCancel.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void tenantOrderManagement_createEditStatsAndTimeline() throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Admin Orders", "admin-orders@test.com", "admin-orders");

        long productId = createProduct(owner, "Arepa Rellena", 10000);

        // Create manual por el restaurante (teléfono/presencial) → 201
        ResponseEntity<JsonNode> created = rest.exchange("/api/orders", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of(
                        "customerName", "Cliente Telefónico",
                        "customerPhone", "3155558877",
                        "tableNumber", "Domicilio",
                        "orderType", "DELIVERY",
                        "items", List.of(Map.of("productId", productId, "quantity", 2))
                ), owner), JsonNode.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        JsonNode data = created.getBody().get("data");
        long orderId = data.get("id").asLong();
        assertThat(data.get("orderNumber").asText()).startsWith("ADMI-");
        assertThat(data.get("orderType").asText()).isEqualTo("DELIVERY");
        assertThat(data.get("totalAmount").asDouble()).isEqualTo(20000.0);
        assertThat(data.get("timeline")).hasSize(1);

        // Edición: cambia datos e ítems recalculando el total
        ResponseEntity<JsonNode> edited = rest.exchange("/api/orders/" + orderId, HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of(
                        "customerName", "Cliente Editado",
                        "tableNumber", "Mesa 9",
                        "items", List.of(Map.of("productId", productId, "quantity", 3))
                ), owner), JsonNode.class);
        assertThat(edited.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(edited.getBody().get("data").get("customerName").asText()).isEqualTo("Cliente Editado");
        assertThat(edited.getBody().get("data").get("tableNumber").asText()).isEqualTo("Mesa 9");
        assertThat(edited.getBody().get("data").get("totalAmount").asDouble()).isEqualTo(30000.0);
        assertThat(edited.getBody().get("data").get("items")).hasSize(1);

        // Items vacíos → 400
        ResponseEntity<JsonNode> emptyEdit = rest.exchange("/api/orders/" + orderId, HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("items", List.of()), owner), JsonNode.class);
        assertThat(emptyEdit.getStatusCode().value()).isEqualTo(400);

        // Stats: total incluye el pedido, hoy incluye el pedido
        ResponseEntity<JsonNode> stats = rest.exchange("/api/orders/stats", HttpMethod.GET, owner.get(), JsonNode.class);
        assertThat(stats.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(stats.getBody().get("data").get("total").asLong()).isEqualTo(1);
        assertThat(stats.getBody().get("data").get("pending").asLong()).isEqualTo(1);
        assertThat(stats.getBody().get("data").get("todayCount").asLong()).isEqualTo(1);
        assertThat(stats.getBody().get("data").get("todayRevenue").asDouble()).isEqualTo(30000.0);

        // Filtro "since": con el pasaso 1 hora trae el pedido; con el futuro no trae nada
        String past = java.time.Instant.now().minusSeconds(3600).toString();
        ResponseEntity<JsonNode> since = rest.exchange("/api/orders?since=" + past, HttpMethod.GET,
                owner.get(), JsonNode.class);
        assertThat(since.getBody().get("data")).hasSize(1);

        String future = java.time.Instant.now().plusSeconds(3600).toString();
        ResponseEntity<JsonNode> sinceFuture = rest.exchange("/api/orders?since=" + future, HttpMethod.GET,
                owner.get(), JsonNode.class);
        assertThat(sinceFuture.getBody().get("data")).isEmpty();

        // Paginación: page/size devuelven el pedido
        ResponseEntity<JsonNode> paged = rest.exchange("/api/orders?page=0&size=1", HttpMethod.GET,
                owner.get(), JsonNode.class);
        assertThat(paged.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(paged.getBody().get("data")).hasSize(1);

        // Cancelado → ya no se puede editar
        rest.exchange("/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "CANCELLED"), owner), JsonNode.class);
        ResponseEntity<JsonNode> editCancelled = rest.exchange("/api/orders/" + orderId, HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("customerName", "Nope"), owner), JsonNode.class);
        assertThat(editCancelled.getStatusCode().value()).isEqualTo(400);

        // Aislamiento: editar pedido ajeno → 404
        TestHttp.Session other = TestHttp.register(rest, objectMapper,
                "Admin Orders Other", "admin-orders-other@test.com", "admin-orders-other");
        ResponseEntity<JsonNode> editOther = rest.exchange("/api/orders/" + orderId, HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("customerName", "Hack"), other), JsonNode.class);
        assertThat(editOther.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void publicOrders_concurrentCreation_hasUniqueNumbers() throws Exception {
        // Crear restaurante con producto y lanzar 2 pedidos concurrentes: el lock
        // pesimista en la fila del restaurante serializa el consecutivo.
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Concurrent Owner", "concurrent-owner@test.com", "concurrent-orders");
        long productId = createProduct(owner, "Concurrent Burger", 15000);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> postPublicOrder("concurrent-orders",
                    Map.of("customerName", "A", "items", List.of(Map.of("productId", productId, "quantity", 1)))));
            pool.submit(() -> postPublicOrder("concurrent-orders",
                    Map.of("customerName", "B", "items", List.of(Map.of("productId", productId, "quantity", 1)))));
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        ResponseEntity<JsonNode> list = rest.exchange("/api/orders", HttpMethod.GET, owner.get(), JsonNode.class);
        assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(list.getBody().get("data")).hasSize(2);

        JsonNode orders = list.getBody().get("data");
        String first = orders.get(0).get("orderNumber").asText();
        String second = orders.get(1).get("orderNumber").asText();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void publicOrders_rateLimit_returns429() throws Exception {
        // Slug dedicado para no contaminar otros tests: 12 peticiones inválidas
        // (cuerpo vacío) a la creación pública; las últimas deben dar 429.
        ResponseEntity<JsonNode> last = null;
        for (int i = 0; i < 12; i++) {
            last = postPublicOrder("rate-limited", Map.of());
        }
        assertThat(last.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    private long createProduct(TestHttp.Session session, String name, int price) throws Exception {
        ResponseEntity<JsonNode> category = rest.exchange("/api/categories", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("name", name + " Cat", "position", 1, "active", true), session),
                JsonNode.class);
        long categoryId = category.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> product = rest.exchange("/api/products", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of(
                        "categoryId", categoryId,
                        "name", name,
                        "price", price,
                        "available", true), session),
                JsonNode.class);
        return product.getBody().get("data").get("id").asLong();
    }

    private ResponseEntity<JsonNode> postPublicOrder(String slug, Map<String, Object> body) {
        HttpEntity<String> entity = new HttpEntity<>(json(body), jsonHeaders());
        return rest.postForEntity("/api/public/orders/" + slug, entity, JsonNode.class);
    }

    private org.springframework.http.HttpHeaders jsonHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private String json(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
