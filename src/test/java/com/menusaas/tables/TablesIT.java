package com.menusaas.tables;

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
 * Mesas del salón: viven en el backend (tenant-scoped) para que todo el
 * equipo vea las mismas, no en el localStorage de cada navegador.
 */
class TablesIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void tables_columnNameMatchesEntity() throws Exception {
        // Regresión: la columna se llamaba table_number en unas bases y "number"
        // en la entidad, y /api/tables devolvía 500 con
        // "column rt1_0.number does not exist". Como ddl-auto=none en prod, nada
        // lo detectaba en el arranque: solo reventaba al usar el endpoint.
        // Este test recorre el CRUD completo contra el esquema real migrado.
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Tables Column", "tables-column@test.com", "tables-column");

        ResponseEntity<JsonNode> created = postTable(owner, Map.of("number", "77", "seats", 2));
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody().get("data").get("number").asText()).isEqualTo("77");

        ResponseEntity<JsonNode> listed = rest.exchange(
                "/api/tables", HttpMethod.GET, owner.get(), JsonNode.class);
        assertThat(listed.getStatusCode().value()).isEqualTo(200);
        assertThat(listed.getBody().get("data")).isNotEmpty();
    }

    @Test
    void tables_crudAndTenantIsolation() throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Tables Owner", "tables-owner@test.com", "tables-owner");
        TestHttp.Session other = TestHttp.register(rest, objectMapper,
                "Tables Other", "tables-other@test.com", "tables-other");

        // Empieza vacío
        assertThat(list(owner)).isEmpty();

        // Crear con puestos; el número se guarda recortado
        ResponseEntity<JsonNode> created = rest.exchange("/api/tables", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("number", " 03 ", "seats", 4), owner), JsonNode.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        JsonNode data = created.getBody().get("data");
        long tableId = data.get("id").asLong();
        assertThat(data.get("number").asText()).isEqualTo("03");
        assertThat(data.get("seats").asInt()).isEqualTo(4);

        // Sin puestos → 2 por defecto
        ResponseEntity<JsonNode> minimal = rest.exchange("/api/tables", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("number", "04"), owner), JsonNode.class);
        assertThat(minimal.getStatusCode().value()).isEqualTo(201);
        assertThat(minimal.getBody().get("data").get("seats").asInt()).isEqualTo(2);

        // Duplicada → 409
        ResponseEntity<JsonNode> dup = rest.exchange("/api/tables", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("number", "03", "seats", 2), owner), JsonNode.class);
        assertThat(dup.getStatusCode().value()).isEqualTo(409);

        // Validación: sin número → 400; puestos fuera de rango → 400
        assertThat(postTable(owner, Map.of("seats", 2)).getStatusCode().value()).isEqualTo(400);
        assertThat(postTable(owner, Map.of("number", "05", "seats", 0)).getStatusCode().value()).isEqualTo(400);
        assertThat(postTable(owner, Map.of("number", "05", "seats", 99)).getStatusCode().value()).isEqualTo(400);

        // Lista las creadas
        assertThat(list(owner)).hasSize(2);

        // Otro restaurante no las ve ni las puede borrar
        assertThat(list(other)).isEmpty();
        assertThat(delete(other, tableId).getStatusCode().value()).isEqualTo(404);

        // Borrar → 200; borrar de nuevo → 404
        assertThat(delete(owner, tableId).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(delete(owner, tableId).getStatusCode().value()).isEqualTo(404);
        assertThat(list(owner)).hasSize(1);
    }

    @Test
    void tables_waiterCanReadButNotManage() throws Exception {
        TestHttp.Session admin = TestHttp.register(rest, objectMapper,
                "Tables Admin", "tables-admin@test.com", "tables-admin");
        ResponseEntity<JsonNode> created = rest.exchange("/api/users", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of(
                        "name", "Mesero Mesas",
                        "email", "mesero-mesas@test.com",
                        "password", "StrongPass123!",
                        "role", "WAITER"), admin),
                JsonNode.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        TestHttp.Session waiter = TestHttp.login(rest, objectMapper,
                "mesero-mesas@test.com", "StrongPass123!", TestHttp.bootstrapCsrf(rest));

        // Leer sí puede…
        assertThat(list(waiter)).isEmpty();
        // …crear y borrar no (sin ORDERS_EDIT)
        assertThat(postTable(waiter, Map.of("number", "01", "seats", 2)).getStatusCode().value())
                .isEqualTo(403);
    }

    private List<JsonNode> list(TestHttp.Session session) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/tables", HttpMethod.GET,
                session.get(), JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<JsonNode> out = new java.util.ArrayList<>();
        response.getBody().get("data").forEach(out::add);
        return out;
    }

    private ResponseEntity<JsonNode> postTable(TestHttp.Session session, Map<String, Object> body) throws Exception {
        return rest.exchange("/api/tables", HttpMethod.POST,
                TestHttp.body(objectMapper, body, session), JsonNode.class);
    }

    private ResponseEntity<JsonNode> delete(TestHttp.Session session, long id) {
        return rest.exchange("/api/tables/" + id, HttpMethod.DELETE, session.get(), JsonNode.class);
    }
}
