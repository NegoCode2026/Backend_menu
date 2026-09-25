package com.menusaas.permissions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Permisos por persona: una persona puede recibir permisos propios que mandan
 * sobre los de su rol; si no se personaliza, hereda los del rol.
 *
 * Comprobado de punta a punta: se cambia el permiso de UNA persona y su
 * sesión real (no la de su compañero con el mismo rol) pasa a poder entrar
 * en caja.
 */
class PermissionsIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void userPermissions_inheritRoleByDefault_andOverridePerPerson() throws Exception {
        TestHttp.Session admin = TestHttp.register(rest, objectMapper,
                "Perm Admin", "perm-admin@test.com", "perm-admin");

        long waiterOneId = createUser(admin, "Mesero Uno", "perm-waiter-one@test.com", "WAITER");
        long waiterTwoId = createUser(admin, "Mesero Dos", "perm-waiter-two@test.com", "WAITER");
        TestHttp.Session waiterOne = login("perm-waiter-one@test.com");
        TestHttp.Session waiterTwo = login("perm-waiter-two@test.com");

        // Sin personalización: el rol WAITER no da CASH_CLOSE → fuera de caja
        assertThat(getCashToday(waiterOne).getStatusCode().value()).isEqualTo(403);
        assertThat(getCashToday(waiterTwo).getStatusCode().value()).isEqualTo(403);

        // GET: por defecto hereda los permisos del rol
        JsonNode inherited = getPermissions(admin, waiterOneId);
        assertThat(inherited.get("inherited").asBoolean()).isTrue();
        assertThat(inherited.get("role").asText()).isEqualTo("WAITER");
        assertThat(texts(inherited.get("permissions"))).containsExactlyInAnyOrder("ORDER_SERVE");

        // PUT: permisos propios de ESTA persona (el rol no cambia)
        ResponseEntity<JsonNode> saved = rest.exchange(
                "/api/permissions/user/" + waiterOneId, HttpMethod.PUT,
                TestHttp.body(objectMapper, Map.of("permissions", List.of("CASH_CLOSE")), admin),
                JsonNode.class);
        assertThat(saved.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode savedData = saved.getBody().get("data");
        assertThat(savedData.get("inherited").asBoolean()).isFalse();
        assertThat(texts(savedData.get("permissions"))).containsExactlyInAnyOrder("CASH_CLOSE");

        // Ahora SÍ puede entrar a caja…
        assertThat(getCashToday(waiterOne).getStatusCode().is2xxSuccessful()).isTrue();
        // …y su compañero con el MISMO rol sigue fuera: cambió la persona, no el rol
        assertThat(getCashToday(waiterTwo).getStatusCode().value()).isEqualTo(403);

        // GET sigue devolviendo lo personalizado
        assertThat(texts(getPermissions(admin, waiterOneId).get("permissions")))
                .containsExactlyInAnyOrder("CASH_CLOSE");
        assertThat(getPermissions(admin, waiterTwoId).get("inherited").asBoolean()).isTrue();

        // Solo permisos válidos: los desconocidos se ignoran
        ResponseEntity<JsonNode> bogus = rest.exchange(
                "/api/permissions/user/" + waiterOneId, HttpMethod.PUT,
                TestHttp.body(objectMapper, Map.of("permissions", List.of("CASH_CLOSE", "PERMISO_FALSO")), admin),
                JsonNode.class);
        assertThat(bogus.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(texts(bogus.getBody().get("data").get("permissions"))).containsExactly("CASH_CLOSE");

        // DELETE: quita la personalización y vuelve a los permisos del rol
        ResponseEntity<JsonNode> cleared = rest.exchange(
                "/api/permissions/user/" + waiterOneId, HttpMethod.DELETE, admin.get(), JsonNode.class);
        assertThat(cleared.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(cleared.getBody().get("data").get("inherited").asBoolean()).isTrue();
        assertThat(getCashToday(waiterOne).getStatusCode().value()).isEqualTo(403);

        // El admin siempre tiene todo: no admite personalización
        long adminId = findUserId(admin, "perm-admin@test.com");
        ResponseEntity<JsonNode> adminOverride = rest.exchange(
                "/api/permissions/user/" + adminId, HttpMethod.PUT,
                TestHttp.body(objectMapper, Map.of("permissions", List.of()), admin),
                JsonNode.class);
        assertThat(adminOverride.getStatusCode().value()).isEqualTo(400);
        assertThat(getPermissions(admin, adminId).get("inherited").asBoolean()).isTrue();
        assertThat(texts(getPermissions(admin, adminId).get("permissions")))
                .contains("CASH_CLOSE", "USERS_MANAGE");

        // Aislamiento entre tenants: un usuario de otro restaurante → 404
        TestHttp.Session otherTenant = TestHttp.register(rest, objectMapper,
                "Perm Other", "perm-other@test.com", "perm-other");
        ResponseEntity<JsonNode> crossTenant = rest.exchange(
                "/api/permissions/user/" + waiterOneId, HttpMethod.GET, otherTenant.get(), JsonNode.class);
        assertThat(crossTenant.getStatusCode().value()).isEqualTo(404);
    }

    private long createUser(TestHttp.Session admin, String name, String email, String role) throws Exception {
        ResponseEntity<JsonNode> created = rest.exchange("/api/users", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of(
                        "name", name,
                        "email", email,
                        "password", "StrongPass123!",
                        "role", role), admin),
                JsonNode.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        return created.getBody().get("data").get("id").asLong();
    }

    private TestHttp.Session login(String email) throws Exception {
        String xsrf = TestHttp.bootstrapCsrf(rest);
        return TestHttp.login(rest, objectMapper, email, "StrongPass123!", xsrf);
    }

    private JsonNode getPermissions(TestHttp.Session admin, long userId) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/permissions/user/" + userId,
                HttpMethod.GET, admin.get(), JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody().get("data");
    }

    private ResponseEntity<JsonNode> getCashToday(TestHttp.Session session) {
        return rest.exchange("/api/cash/today", HttpMethod.GET, session.get(), JsonNode.class);
    }

    private long findUserId(TestHttp.Session admin, String email) {
        ResponseEntity<JsonNode> list = rest.exchange("/api/users", HttpMethod.GET, admin.get(), JsonNode.class);
        assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
        for (JsonNode user : list.getBody().get("data")) {
            if (email.equals(user.get("email").asText())) {
                return user.get("id").asLong();
            }
        }
        throw new AssertionError("No se encontró el usuario " + email);
    }

    private List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
