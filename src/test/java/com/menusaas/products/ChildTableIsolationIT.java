package com.menusaas.products;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aislamiento de las tablas hijas (sin columna restaurant_id).
 *
 * <p>{@code recipe_items}, {@code order_items}, {@code order_status_history} y
 * {@code stored_files} no tienen {@code restaurant_id}: se consultan por su id
 * de padre. El patrón que las mantiene seguras es validar primero el padre
 * con el tenant, y solo después tocar la tabla sin tenant.
 *
 * <p>Ese patrón no lo cubre {@code TenantRepositoriesTest}, que comprueba por
 * reflexión los nombres de los métodos del repositorio. Aquí se comprueba en
 * ejecución, con dos tenants reales.
 *
 * <p>Ademas deja explicito por que las imagenes no necesitan esto: se sirven con
 * URL firmada, y la firma cubre el fileId, asi que un tenant no puede pedir la
 * imagen de otro aunque conozca el identificador.
 */
class ChildTableIsolationIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    private long createProduct(TestHttp.Session session, String name) throws Exception {
        ResponseEntity<JsonNode> created = rest.exchange("/api/products", HttpMethod.POST,
                TestHttp.body(objectMapper, (Object) Map.of(
                        "name", name, "price", 15000, "available", true), session), JsonNode.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        return created.getBody().get("data").get("id").asLong();
    }

    @Test
    void recipeOfAnotherTenant_isNotReadable() throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Recipe Owner", "recipe-owner@test.com", "recipe-owner");
        TestHttp.Session other = TestHttp.register(rest, objectMapper,
                "Recipe Other", "recipe-other@test.com", "recipe-other");

        long productId = createProduct(owner, "Plato con receta");

        // El propietario si ve su receta.
        ResponseEntity<JsonNode> own = rest.exchange("/api/products/" + productId + "/recipe",
                HttpMethod.GET, owner.get(), JsonNode.class);
        assertThat(own.getStatusCode().is2xxSuccessful()).isTrue();

        // Un tercero, no.
        ResponseEntity<JsonNode> cross = rest.exchange("/api/products/" + productId + "/recipe",
                HttpMethod.GET, other.get(), JsonNode.class);
        assertThat(cross.getStatusCode().value())
                .as("la receta se valida contra el padre antes de consultarse")
                .isEqualTo(404);
    }

    @Test
    void orderDetailOfAnotherTenant_isNotReadable() throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Order Owner", "child-order-a@test.com", "child-order-a");
        TestHttp.Session other = TestHttp.register(rest, objectMapper,
                "Order Other", "child-order-b@test.com", "child-order-b");

        long productId = createProduct(owner, "Plato pedido");
        ResponseEntity<JsonNode> order = rest.exchange("/api/orders", HttpMethod.POST,
                TestHttp.body(objectMapper, (Object) Map.of(
                        "customerName", "Cliente",
                        "items", List.of(Map.of("productId", productId, "quantity", 1))), owner),
                JsonNode.class);
        assertThat(order.getStatusCode().value()).isEqualTo(201);
        long orderId = order.getBody().get("data").get("id").asLong();

        // Los items del pedido son cross-tenant por id: solo el padre los protege.
        ResponseEntity<JsonNode> cross = rest.exchange("/api/orders/" + orderId,
                HttpMethod.GET, other.get(), JsonNode.class);
        assertThat(cross.getStatusCode().value()).isEqualTo(404);
        assertThat(cross.getBody().has("data")).isFalse();
    }

    @Test
    void ingredientOfAnotherTenant_cannotBeModifiedOrDeleted() throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Inv Owner", "inv-owner@test.com", "inv-owner");
        TestHttp.Session other = TestHttp.register(rest, objectMapper,
                "Inv Other", "inv-other@test.com", "inv-other");

        ResponseEntity<JsonNode> ingredient = rest.exchange("/api/inventory/ingredients",
                HttpMethod.POST,
                TestHttp.body(objectMapper, (Object) Map.of(
                        "name", "Tomate secreto", "unit", "kg",
                        "stockQuantity", 10, "lowStockThreshold", 2), owner), JsonNode.class);
        assertThat(ingredient.getStatusCode().value()).isEqualTo(201);
        long ingredientId = ingredient.getBody().get("data").get("id").asLong();

        // Un tercero no puede alterarlo.
        ResponseEntity<JsonNode> put = rest.exchange(
                "/api/inventory/ingredients/" + ingredientId, HttpMethod.PUT,
                TestHttp.body(objectMapper, (Object) Map.of(
                        "name", "Robado", "unit", "kg",
                        "stockQuantity", 999, "lowStockThreshold", 1), other), JsonNode.class);
        assertThat(put.getStatusCode().value()).isEqualTo(404);

        ResponseEntity<JsonNode> delete = rest.exchange(
                "/api/inventory/ingredients/" + ingredientId, HttpMethod.DELETE,
                other.get(), JsonNode.class);
        assertThat(delete.getStatusCode().value()).isEqualTo(404);
    }

    /**
     * Un verbo no admitido sobre una ruta existente debe ser 405, no 500.
     * GET /ingredients/{id} no existe porque solo hay PUT y DELETE; antes caía en
     * el handler genérico y el cliente recibía un error de servidor.
     */
    @Test
    void unsupportedVerbOnExistingPath_isMethodNotAllowed_notServerError() throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Verb Owner", "verb-owner@test.com", "verb-owner");

        ResponseEntity<JsonNode> response = rest.exchange(
                "/api/inventory/ingredients/1", HttpMethod.GET, owner.get(), JsonNode.class);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().get("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
    }
}