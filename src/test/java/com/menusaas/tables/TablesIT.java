package com.menusaas.tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mesas MVP: CRUD tenant-scoped, validación pública por code UUID,
 * integración con pedidos (tableCode prevalece, legacy sin código sigue)
 * y borrado físico con snapshot histórico.
 */
class TablesIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void tables_crud_resolve_andOrders() throws Exception {
        TestHttp.Session ownerA = TestHttp.register(rest, objectMapper,
                "Tables A", "tables-a@test.com", "tables-a");
        TestHttp.Session ownerB = TestHttp.register(rest, objectMapper,
                "Tables B", "tables-b@test.com", "tables-b");

        // Crear mesa
        ResponseEntity<JsonNode> created = rest.exchange("/api/tables", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("label", "Mesa 1"), ownerA), JsonNode.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        long tableId = created.getBody().get("data").get("id").asLong();
        String code = created.getBody().get("data").get("code").asText();
        assertThat(code).isNotBlank();

        // Label duplicado -> 409
        ResponseEntity<JsonNode> dup = rest.exchange("/api/tables", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("label", "mesa 1"), ownerA), JsonNode.class);
        assertThat(dup.getStatusCode().value()).isEqualTo(409);

        // Listado propio (1, con menuUrl para dibujar el QR), ajeno (0) y detalle cruzado -> 404
        JsonNode mine = listTables(ownerA);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("menuUrl").asText()).contains("/menu/tables-a").contains(code);
        assertThat(mine.get(0).get("code").asText()).isEqualTo(code);
        assertThat(listTables(ownerB)).isEmpty();
        ResponseEntity<JsonNode> cross = rest.exchange("/api/tables/" + tableId, HttpMethod.GET,
                ownerB.get(), JsonNode.class);
        assertThat(cross.getStatusCode().value()).isEqualTo(404);

        // qr-info para que el front dibuje el QR
        ResponseEntity<JsonNode> qrInfo = rest.exchange("/api/tables/" + tableId + "/qr-info",
                HttpMethod.GET, ownerA.get(), JsonNode.class);
        assertThat(qrInfo.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(qrInfo.getBody().get("data").get("menuUrl").asText())
                .contains("/menu/tables-a").contains(code);

        // Resolve público del ?t={code}
        ResponseEntity<JsonNode> resolved = rest.getForEntity(
                "/api/public/tables/resolve?code=" + code, JsonNode.class);
        assertThat(resolved.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resolved.getBody().get("data").get("restaurantSlug").asText()).isEqualTo("tables-a");
        assertThat(resolved.getBody().get("data").get("tableLabel").asText()).isEqualTo("Mesa 1");

        // Code inexistente -> 404
        ResponseEntity<JsonNode> unknown = rest.getForEntity(
                "/api/public/tables/resolve?code=" + UUID.randomUUID(), JsonNode.class);
        assertThat(unknown.getStatusCode().value()).isEqualTo(404);

        // Producto para pedir en tables-a
        long productId = createProduct(ownerA, "Bebidas", "Limonada", 5000);

        // Pedido con tableCode válido -> mesa real vinculada
        ResponseEntity<JsonNode> order = postPublicOrder("tables-a", Map.of(
                "customerName", "Juan",
                "tableCode", code,
                "orderType", "DINE_IN",
                "items", List.of(Map.of("productId", productId, "quantity", 1))));
        assertThat(order.getStatusCode().value()).isEqualTo(201);
        assertThat(order.getBody().get("data").get("tableId").asLong()).isEqualTo(tableId);
        assertThat(order.getBody().get("data").get("tableNumber").asText()).isEqualTo("Mesa 1");

        // tableCode de otro restaurante -> 400
        ResponseEntity<JsonNode> foreign = postPublicOrder("tables-b", Map.of(
                "customerName", "X",
                "tableCode", code,
                "items", List.of(Map.of("productId", productId, "quantity", 1))));
        assertThat(foreign.getStatusCode().value()).isEqualTo(400);

        // tableCode inexistente -> 404
        ResponseEntity<JsonNode> badCode = postPublicOrder("tables-a", Map.of(
                "customerName", "X",
                "tableCode", UUID.randomUUID().toString(),
                "items", List.of(Map.of("productId", productId, "quantity", 1))));
        assertThat(badCode.getStatusCode().value()).isEqualTo(404);

        // Legacy sin tableCode sigue funcionando (tableId null)
        ResponseEntity<JsonNode> legacy = postPublicOrder("tables-a", Map.of(
                "customerName", "Ana",
                "tableNumber", "Terraza",
                "items", List.of(Map.of("productId", productId, "quantity", 1))));
        assertThat(legacy.getStatusCode().value()).isEqualTo(201);
        assertThat(legacy.getBody().get("data").get("tableNumber").asText()).isEqualTo("Terraza");
        assertThat(legacy.getBody().get("data").get("tableId").isNull()).isTrue();

        // Regenerar invalida el QR anterior
        ResponseEntity<JsonNode> regen = rest.exchange("/api/tables/" + tableId + "/regenerate-code",
                HttpMethod.POST, ownerA.get(), JsonNode.class);
        assertThat(regen.getStatusCode().is2xxSuccessful()).isTrue();
        String newCode = regen.getBody().get("data").get("code").asText();
        assertThat(newCode).isNotEqualTo(code);
        assertThat(rest.getForEntity("/api/public/tables/resolve?code=" + code, JsonNode.class)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(rest.getForEntity("/api/public/tables/resolve?code=" + newCode, JsonNode.class)
                .getStatusCode().is2xxSuccessful()).isTrue();

        // Renombrar no cambia el code
        ResponseEntity<JsonNode> renamed = rest.exchange("/api/tables/" + tableId, HttpMethod.PUT,
                TestHttp.body(objectMapper, Map.of("label", "Mesa VIP"), ownerA), JsonNode.class);
        assertThat(renamed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(renamed.getBody().get("data").get("code").asText()).isEqualTo(newCode);

        // Borrar: resolve 404 pero el pedido histórico conserva el snapshot
        ResponseEntity<JsonNode> deleted = rest.exchange("/api/tables/" + tableId, HttpMethod.DELETE,
                ownerA.get(), JsonNode.class);
        assertThat(deleted.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rest.getForEntity("/api/public/tables/resolve?code=" + newCode, JsonNode.class)
                .getStatusCode().value()).isEqualTo(404);
    }

    private JsonNode listTables(TestHttp.Session session) {
        ResponseEntity<JsonNode> list = rest.exchange("/api/tables", HttpMethod.GET,
                session.get(), JsonNode.class);
        assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
        return list.getBody().get("data");
    }

    private long createProduct(TestHttp.Session owner, String category, String product, int price)
            throws Exception {
        ResponseEntity<JsonNode> cat = rest.exchange("/api/categories", HttpMethod.POST,
                TestHttp.body(objectMapper,
                        Map.of("name", category, "position", 1, "active", true), owner),
                JsonNode.class);
        long categoryId = cat.getBody().get("data").get("id").asLong();
        ResponseEntity<JsonNode> prod = rest.exchange("/api/products", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of(
                        "categoryId", categoryId,
                        "name", product,
                        "price", price,
                        "available", true), owner),
                JsonNode.class);
        return prod.getBody().get("data").get("id").asLong();
    }

    private ResponseEntity<JsonNode> postPublicOrder(String slug, Map<String, Object> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/public/orders/" + slug,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), JsonNode.class);
    }
}
