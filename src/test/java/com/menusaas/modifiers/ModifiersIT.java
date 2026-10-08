package com.menusaas.modifiers;

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
 * Modifiers: el precio sale del servidor, nunca del cliente.
 *
 * <p>Sin esto no se puede publicar una carta real (tamaños, término, extras).
 * Y el riesgo que hay que vigilar aquí es obvio: si el payload pudiera dictar
 * el precio, cualquiera con un curl pagaría lo que quisiera.
 */
class ModifiersIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private long groupId;
    private long pequenoId;
    private long grandeId;

    /** Monta: grupo "Tamaño" con Pequeño (+0) y Grande (+3000). */
    private TestHttp.Session setUp(String slug) throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Chef " + slug, slug + "@test.com", slug);

        ResponseEntity<JsonNode> product = post(owner, "/api/products", Map.of(
                "name", "Hamburguesa", "price", 18000, "available", true));
        long productId = product.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> group = post(owner, "/api/modifiers/groups", Map.of(
                "name", "Tamaño", "minSelections", 1, "maxSelections", 1, "required", true));
        // Diagnóstico: si el alta falla, saber por qué y no un NPE más abajo.
        assertThat(group.getStatusCode().value())
                .as("alta de grupo de opciones: %s", group.getBody())
                .isEqualTo(200);
        groupId = group.getBody().get("data").get("id").asLong();

        pequenoId = post(owner, "/api/modifiers/groups/" + groupId + "/options",
                Map.of("name", "Pequeño", "priceDelta", 0)).getBody().get("data").get("id").asLong();
        grandeId = post(owner, "/api/modifiers/groups/" + groupId + "/options",
                Map.of("name", "Grande", "priceDelta", 3000)).getBody().get("data").get("id").asLong();

        put(owner, "/api/modifiers/product/" + productId + "/groups", List.of(groupId));
        return owner;
    }

    private ResponseEntity<JsonNode> post(TestHttp.Session s, String path, Object body) throws Exception {
        return rest.exchange(path, HttpMethod.POST,
                TestHttp.body(objectMapper, (Object) body, s), JsonNode.class);
    }

    private ResponseEntity<JsonNode> put(TestHttp.Session s, String path, Object body) throws Exception {
        return rest.exchange(path, HttpMethod.PUT,
                TestHttp.body(objectMapper, (Object) body, s), JsonNode.class);
    }

    private ResponseEntity<JsonNode> createOrder(TestHttp.Session s, Long productId, List<Map<String, Object>> mods)
            throws Exception {
        return post(s, "/api/orders", Map.of(
                "customerName", "Cliente",
                "items", List.of(Map.of(
                        "productId", productId, "quantity", 1, "modifiers", mods))));
    }

    @Test
    void modifierPriceComesFromTheServer() throws Exception {
        TestHttp.Session owner = setUp("mod-precio-1");
        Long productId = jdbc.queryForObject(
                "select id from products where restaurant_id=(select id from restaurants where slug='mod-precio-1')",
                Long.class);

        // El cliente pide "Grande" (+3000): 18000 + 3000.
        ResponseEntity<JsonNode> order = createOrder(owner, productId,
                List.of(Map.of("modifierId", grandeId, "quantity", 1)));
        assertThat(order.getStatusCode().value()).isEqualTo(201);

        JsonNode data = order.getBody().get("data");
        assertThat(data.get("totalAmount").asDouble()).isEqualTo(21000.0);

        // Y las opciones vuelven en la respuesta: el cocina necesita verlas.
        JsonNode modifiers = data.get("items").get(0).get("modifiers");
        assertThat(modifiers).hasSize(1);
        assertThat(modifiers.get(0).get("modifierName").asText()).isEqualTo("Grande");
        assertThat(modifiers.get(0).get("priceDelta").asDouble()).isEqualTo(3000.0);
        assertThat(modifiers.get(0).get("groupName").asText()).isEqualTo("Tamaño");
    }

    @Test
    void clientCannotInventAPrice() throws Exception {
        TestHttp.Session owner = setUp("mod-precio-2");
        Long productId = jdbc.queryForObject(
                "select id from products where restaurant_id=(select id from restaurants where slug='mod-precio-2')",
                Long.class);

        // Se intenta pasar el precio por el payload: el servidor lo ignora y cobra
        // el de la tabla.
        ResponseEntity<JsonNode> order = post(owner, "/api/orders", Map.of(
                "customerName", "Atacante",
                "items", List.of(Map.of(
                        "productId", productId, "quantity", 1,
                        "modifiers", List.of(Map.of("modifierId", pequenoId, "quantity", 1,
                                "priceDelta", -17000, "price", 1))))));

        assertThat(order.getStatusCode().value()).isEqualTo(201);
        assertThat(order.getBody().get("data").get("totalAmount").asDouble())
                .as("un priceDelta enviado por el cliente no puede alterar el cobro")
                .isEqualTo(18000.0);
    }

    @Test
    void requiredGroupMustBeChosen() throws Exception {
        TestHttp.Session owner = setUp("mod-obl-1");
        Long productId = jdbc.queryForObject(
                "select id from products where restaurant_id=(select id from restaurants where slug='mod-obl-1')",
                Long.class);

        ResponseEntity<JsonNode> order = createOrder(owner, productId, List.of());

        assertThat(order.getStatusCode().value()).isEqualTo(400);
        assertThat(order.getBody().get("message").asText()).contains("Tamaño");
    }

    @Test
    void cannotUseAnOptionFromAnotherProduct() throws Exception {
        TestHttp.Session owner = setUp("mod-otro-1");
        // Un producto sin grupos asignado no puede usar las opciones de otro.
        Long otroProducto = post(owner, "/api/products", Map.of(
                "name", "Bebida", "price", 5000, "available", true))
                .getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> order = createOrder(owner, otroProducto,
                List.of(Map.of("modifierId", grandeId, "quantity", 1)));

        assertThat(order.getStatusCode().value()).isEqualTo(400);
        assertThat(order.getBody().get("message").asText()).contains("no pertenece a este producto");
    }

    @Test
    void publicMenuExposesTheGroups() throws Exception {
        setUp("mod-publico-1");

        ResponseEntity<JsonNode> menu = rest.getForEntity(
                "/api/public/menu/mod-publico-1", JsonNode.class);

        assertThat(menu.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode products = menu.getBody().get("data").get("categories").get(0).get("products");
        assertThat(products).isNotEmpty();

        JsonNode groups = products.get(0).get("modifierGroups");
        assertThat(groups).as("el cliente necesita ver los grupos para elegir").isNotEmpty();
        assertThat(groups.get(0).get("name").asText()).isEqualTo("Tamaño");
        assertThat(groups.get(0).get("required").asBoolean()).isTrue();
        assertThat(groups.get(0).get("options")).hasSize(2);

        // El precio base no se infla con las opciones: se suman al elegir.
        assertThat(products.get(0).get("price").asDouble()).isEqualTo(18000.0);
    }

    @Test
    void modifierFromAnotherTenant_isRejected() throws Exception {
        TestHttp.Session ownerA = setUp("mod-alias-1");
        TestHttp.Session ownerB = TestHttp.register(rest, objectMapper,
                "Chef B", "mod-alias-b@test.com", "mod-alias-b");
        Long productB = post(ownerB, "/api/products", Map.of(
                "name", "Otro", "price", 1000, "available", true))
                .getBody().get("data").get("id").asLong();

        // B intenta usar la opción que creó A.
        ResponseEntity<JsonNode> order = createOrder(ownerB, productB,
                List.of(Map.of("modifierId", grandeId, "quantity", 1)));

        assertThat(order.getStatusCode().value()).isEqualTo(400);
    }
}