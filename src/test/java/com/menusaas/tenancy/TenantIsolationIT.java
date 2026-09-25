package com.menusaas.tenancy;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La regla de oro del SaaS: un restaurante JAMÁS ve datos de otro.
 */
class TenantIsolationIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void restaurantA_cannotReadOrMutate_restaurantB_data() throws Exception {
        // Dos restaurantes registrados (cookies HttpOnly, como el frontend real)
        TestHttp.Session sessionA = TestHttp.register(rest, objectMapper, "Rest A", "resta@example.com", "resta");
        TestHttp.Session sessionB = TestHttp.register(rest, objectMapper, "Rest B", "restb@example.com", "restb");

        // A crea una categoría
        ResponseEntity<JsonNode> createCatA = rest.exchange("/api/categories", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("name", "Cat A", "position", "1"), sessionA), JsonNode.class);
        assertThat(createCatA.getStatusCode().is2xxSuccessful()).isTrue();
        long catAId = createCatA.getBody().get("data").get("id").asLong();

        // B intenta obtener la categoría de A → 404
        ResponseEntity<JsonNode> stealCat = rest.exchange("/api/categories/" + catAId, HttpMethod.GET,
                sessionB.get(), JsonNode.class);
        assertThat(stealCat.getStatusCode().value()).isEqualTo(404);

        // A crea un producto en su categoría
        ResponseEntity<JsonNode> createProdA = rest.exchange("/api/products", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("categoryId", String.valueOf(catAId),
                        "name", "Producto A", "price", "1000"), sessionA), JsonNode.class);
        assertThat(createProdA.getStatusCode().is2xxSuccessful()).isTrue();
        long prodAId = createProdA.getBody().get("data").get("id").asLong();

        // B intenta actualizar el producto de A → 404
        ResponseEntity<JsonNode> stealProd = rest.exchange("/api/products/" + prodAId, HttpMethod.PUT,
                TestHttp.body(objectMapper, Map.of("categoryId", String.valueOf(catAId),
                        "name", "Robado", "price", "1"), sessionB), JsonNode.class);
        assertThat(stealProd.getStatusCode().value()).isEqualTo(404);

        // B lista sus categorías: no ve ninguna de A
        ResponseEntity<JsonNode> listB = rest.exchange("/api/categories", HttpMethod.GET,
                sessionB.get(), JsonNode.class);
        assertThat(listB.getBody().get("data").get("content")).isEmpty();

        // A no puede usar una categoría de otro restaurante en un producto
        TestHttp.Session sessionC = TestHttp.register(rest, objectMapper, "Rest C", "restc@example.com", "restc");
        ResponseEntity<JsonNode> crossTenant = rest.exchange("/api/products", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("categoryId", String.valueOf(catAId),
                        "name", "X", "price", "1"), sessionC), JsonNode.class);
        assertThat(crossTenant.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void publicMenu_exposesOnlyActiveRestaurant_andReturns404ForUnknownSlug() throws Exception {
        TestHttp.register(rest, objectMapper, "Public", "publica@example.com", "publica");

        ResponseEntity<JsonNode> menu = rest.getForEntity("/api/public/menu/publica", JsonNode.class);
        assertThat(menu.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(menu.getBody().get("data").get("restaurant").get("name").asText()).isNotBlank();

        ResponseEntity<JsonNode> unknown = rest.getForEntity("/api/public/menu/no-existe", JsonNode.class);
        assertThat(unknown.getStatusCode().value()).isEqualTo(404);

        // Sin cookie/token NO se puede acceder a rutas privadas
        ResponseEntity<JsonNode> protectedCall = rest.exchange("/api/categories", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), JsonNode.class);
        assertThat(protectedCall.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void crossTenant_inventory_cash_reports_qr_users_areIsolated() throws Exception {
        TestHttp.Session sessionA = TestHttp.register(rest, objectMapper, "Iso A", "isoa@example.com", "iso-qr-a");
        TestHttp.Session sessionB = TestHttp.register(rest, objectMapper, "Iso B", "isob@example.com", "iso-qr-b");

        // --- inventory: ingrediente de A invisible e intocable para B ---
        ResponseEntity<JsonNode> createIng = rest.exchange("/api/inventory/ingredients", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("name", "Queso A", "unit", "g"), sessionA), JsonNode.class);
        assertThat(createIng.getStatusCode().value()).isEqualTo(201);
        long ingAId = createIng.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> listIngB = rest.exchange("/api/inventory/ingredients", HttpMethod.GET,
                sessionB.get(), JsonNode.class);
        assertThat(listIngB.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(listIngB.getBody().get("data")).isEmpty();

        ResponseEntity<JsonNode> updateForeignIng = rest.exchange("/api/inventory/ingredients/" + ingAId,
                HttpMethod.PUT, TestHttp.body(objectMapper, Map.of("name", "Robado"), sessionB), JsonNode.class);
        assertThat(updateForeignIng.getStatusCode().value()).isEqualTo(404);

        ResponseEntity<JsonNode> adjustForeignIng = rest.exchange(
                "/api/inventory/ingredients/" + ingAId + "/adjust", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("quantity", 500), sessionB), JsonNode.class);
        assertThat(adjustForeignIng.getStatusCode().value()).isEqualTo(404);

        ResponseEntity<JsonNode> adjustOwnIng = rest.exchange(
                "/api/inventory/ingredients/" + ingAId + "/adjust", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("quantity", 500), sessionA), JsonNode.class);
        assertThat(adjustOwnIng.getStatusCode().is2xxSuccessful()).isTrue();

        // --- cash: cada restaurante solo ve su propio historial ---
        ResponseEntity<JsonNode> closeB = rest.exchange("/api/cash/close", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("countedCash", 1000), sessionB), JsonNode.class);
        assertThat(closeB.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> closingsB = rest.exchange("/api/cash/closings", HttpMethod.GET,
                sessionB.get(), JsonNode.class);
        assertThat(closingsB.getBody().get("data").get("content")).hasSize(1);

        ResponseEntity<JsonNode> closingsA = rest.exchange("/api/cash/closings", HttpMethod.GET,
                sessionA.get(), JsonNode.class);
        assertThat(closingsA.getBody().get("data").get("content")).isEmpty();

        ResponseEntity<JsonNode> todayA = rest.exchange("/api/cash/today", HttpMethod.GET,
                sessionA.get(), JsonNode.class);
        assertThat(todayA.getStatusCode().is2xxSuccessful()).isTrue();

        // --- qr: cada restaurante obtiene su propio slug ---
        ResponseEntity<JsonNode> qrA = rest.exchange("/api/qr/url", HttpMethod.GET,
                sessionA.get(), JsonNode.class);
        assertThat(qrA.getBody().get("data").get("url").asText()).contains("/menu/iso-qr-a");

        ResponseEntity<JsonNode> qrB = rest.exchange("/api/qr/url", HttpMethod.GET,
                sessionB.get(), JsonNode.class);
        assertThat(qrB.getBody().get("data").get("url").asText()).contains("/menu/iso-qr-b");

        // --- users: B no ve a los usuarios de A; un WAITER no gestiona usuarios ---
        ResponseEntity<JsonNode> usersB = rest.exchange("/api/users", HttpMethod.GET,
                sessionB.get(), JsonNode.class);
        assertThat(usersB.getStatusCode().is2xxSuccessful()).isTrue();
        for (JsonNode u : usersB.getBody().get("data")) {
            assertThat(u.get("email").asText()).isNotEqualTo("isoa@example.com");
        }

        ResponseEntity<JsonNode> createWaiter = rest.exchange("/api/users", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of(
                        "name", "Mesero A",
                        "email", "mesero-iso-a@example.com",
                        "password", "StrongPass123!",
                        "role", "WAITER"), sessionA), JsonNode.class);
        assertThat(createWaiter.getStatusCode().value()).isEqualTo(201);

        TestHttp.Session waiterA = TestHttp.login(rest, objectMapper,
                "mesero-iso-a@example.com", "StrongPass123!", TestHttp.bootstrapCsrf(rest));
        ResponseEntity<JsonNode> waiterList = rest.exchange("/api/users", HttpMethod.GET,
                waiterA.get(), JsonNode.class);
        assertThat(waiterList.getStatusCode().value()).isEqualTo(403);

        // --- reports: el pedido entregado de A solo suma en A ---
        long productA = createProduct(sessionA, "Aislado", 5000);
        ResponseEntity<JsonNode> order = rest.exchange("/api/orders", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of(
                        "customerName", "Cliente Aislado",
                        "items", java.util.List.of(Map.of("productId", productA, "quantity", 1))),
                        sessionA),
                JsonNode.class);
        assertThat(order.getStatusCode().value()).isEqualTo(201);
        long orderAId = order.getBody().get("data").get("id").asLong();
        for (String status : new String[]{"CONFIRMED", "IN_PREPARATION", "READY", "DELIVERED"}) {
            ResponseEntity<JsonNode> transition = rest.exchange("/api/orders/" + orderAId + "/status",
                    HttpMethod.PATCH, TestHttp.body(objectMapper, Map.of("status", status), sessionA),
                    JsonNode.class);
            assertThat(transition.getStatusCode().is2xxSuccessful()).isTrue();
        }

        ResponseEntity<JsonNode> profitsA = rest.exchange("/api/reports/profits?period=day",
                HttpMethod.GET, sessionA.get(), JsonNode.class);
        assertThat(profitsA.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(profitsA.getBody().get("data").get("orders").asLong()).isEqualTo(1);

        ResponseEntity<JsonNode> profitsB = rest.exchange("/api/reports/profits?period=day",
                HttpMethod.GET, sessionB.get(), JsonNode.class);
        assertThat(profitsB.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(profitsB.getBody().get("data").get("orders").asLong()).isEqualTo(0);
    }

    private long createProduct(TestHttp.Session session, String name, int price) throws Exception {
        ResponseEntity<JsonNode> category = rest.exchange("/api/categories", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("name", name + " Cat", "position", 1, "active", true),
                        session),
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
}