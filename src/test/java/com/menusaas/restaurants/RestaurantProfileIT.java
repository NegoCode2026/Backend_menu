package com.menusaas.restaurants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import com.menusaas.restaurants.dto.RestaurantRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Datos del restaurante que el formulario enviaba y el backend descartaba.
 */
class RestaurantProfileIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    private ResponseEntity<JsonNode> update(TestHttp.Session session, Map<String, Object> body)
            throws Exception {
        return rest.exchange("/api/restaurants/me", HttpMethod.PUT,
                TestHttp.body(objectMapper, (Object) body, session), JsonNode.class);
    }

    @Test
    void taxIdAndPrepTime_arePersisted() throws Exception {
        // El formulario tenía ambos campos con Validators.required y los mandaba
        // en PUT /restaurants/me, pero no existían ni la columna ni el campo en el
        // DTO: se aceptaban y se descartaban. El tiempo que ve el cliente era
        // siempre el literal "20-30 min" y la factura salía sin NIT.
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Restaurant Owner", "restaurant-profile@test.com", "restaurant-profile");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Restaurante Profile");
        body.put("slug", "restaurant-profile");
        body.put("taxId", "900123456-7");
        body.put("estimatedPrepTime", "15-20 min");

        ResponseEntity<JsonNode> updated = update(owner, body);
        assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(updated.getBody().get("data").get("taxId").asText()).isEqualTo("900123456-7");
        assertThat(updated.getBody().get("data").get("estimatedPrepTime").asText())
                .isEqualTo("15-20 min");

        // Y persiste de verdad: se lee de nuevo desde la base.
        ResponseEntity<JsonNode> reread = rest.exchange(
                "/api/restaurants/me", HttpMethod.GET, owner.get(), JsonNode.class);
        assertThat(reread.getBody().get("data").get("taxId").asText()).isEqualTo("900123456-7");
        assertThat(reread.getBody().get("data").get("estimatedPrepTime").asText())
                .isEqualTo("15-20 min");
    }

    @Test
    void taxIdAndPrepTime_areOptional() throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Restaurant Minimal", "restaurant-minimal@test.com", "restaurant-minimal");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Restaurante Minimo");
        body.put("slug", "restaurant-minimal");

        ResponseEntity<JsonNode> updated = update(owner, body);
        assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(updated.getBody().get("data").get("taxId").isNull()).isTrue();
    }

    @Test
    void activeFlagIsNotPartOfTheTenantUpdateContract() throws Exception {
        // El interruptor de suspensión del SaaS: solo un SUPER_ADMIN debe
        // cambiarlo (PATCH /api/admin/restaurants/{id}/active). Estar en el DTO
        // de actualización permitía que un restaurante suspendido se reactivara
        // solo con un PUT /api/restaurants/me {"active": true}.
        boolean hasActive = java.util.Arrays.stream(RestaurantRequest.class.getRecordComponents())
                .anyMatch(c -> c.getName().equals("active"));
        assertThat(hasActive)
                .as("'active' no debe aceptarse desde el tenant: es el kill-switch del SaaS")
                .isFalse();

        // Y mandarlo en el cuerpo se ignora, no rompe nada ni reactiva.
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Restaurant Active", "restaurant-active@test.com", "restaurant-active");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Restaurante Ignore Flag");
        body.put("slug", "restaurant-active");
        body.put("active", true);

        ResponseEntity<JsonNode> updated = update(owner, body);
        assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();
        // El valor sigue siendo el de registro, no el enviado en el cuerpo.
        assertThat(updated.getBody().get("data").get("active").asBoolean())
                .isEqualTo(rest.exchange("/api/restaurants/me", HttpMethod.GET, owner.get(), JsonNode.class)
                        .getBody().get("data").get("active").asBoolean());
    }

    @Test
    void publicMenuExposesPrepTimeAndTaxId() throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Restaurant Public", "restaurant-public@test.com", "restaurant-public");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Restaurante Publico");
        body.put("slug", "restaurant-public");
        body.put("taxId", "901234567");
        body.put("estimatedPrepTime", "25-35 min");
        update(owner, body);

        ResponseEntity<JsonNode> menu = rest.getForEntity(
                "/api/public/menu/restaurant-public", JsonNode.class);
        assertThat(menu.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode restaurant = menu.getBody().get("data").get("restaurant");
        assertThat(restaurant.get("estimatedPrepTime").asText()).isEqualTo("25-35 min");
        assertThat(restaurant.get("taxId").asText()).isEqualTo("901234567");
    }
}