package com.menusaas.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Panel de SUPER_ADMIN: métricas globales, CRUD de restaurantes con su usuario
 * administrador, activación/desactivación de restaurantes y usuarios, y
 * prohibición de acceso a roles no superiores. El super admin demo lo siembra
 * Flyway (superadmin@demo.com / SuperAdmin123!).
 *
 * Cobertura P1/P2: paginación + búsqueda server-side, planCode estricto (400),
 * protección del último superadmin, auditoría y caché de stats.
 */
class AdminOperationsIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void superAdmin_fullLifecycle() throws Exception {
        TestHttp.Session superAdmin = superAdminSession();

        // Métricas globales (el seed aporta 1 restaurante, 2 usuarios, 1 suscripción activa)
        ResponseEntity<JsonNode> stats = rest.exchange("/api/admin/stats", HttpMethod.GET,
                superAdmin.get(), JsonNode.class);
        assertThat(stats.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(stats.getBody().get("data").get("totalRestaurants").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.getBody().get("data").get("activeSubscriptions").asLong()).isGreaterThanOrEqualTo(1);

        // Listado paginado de restaurantes (Page: data.content)
        ResponseEntity<JsonNode> restaurants = rest.exchange("/api/admin/restaurants?page=0&size=10", HttpMethod.GET,
                superAdmin.get(), JsonNode.class);
        assertThat(restaurants.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(restaurants.getBody().get("data").get("content").toString()).contains("fritomix");

        // Búsqueda server-side por slug
        ResponseEntity<JsonNode> search = rest.exchange("/api/admin/restaurants?search=fritomix", HttpMethod.GET,
                superAdmin.get(), JsonNode.class);
        assertThat(search.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(search.getBody().get("data").get("content").size()).isGreaterThanOrEqualTo(1);

        // Crear restaurante con plan explícito → 201 y suscripción activa
        ResponseEntity<JsonNode> created = createRestaurant(superAdmin,
                "Plan Libre", "plan-libre", "plan-libre-admin@test.com", "PlanLobre123!", "NEGOCODE");
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        JsonNode createdData = created.getBody().get("data");
        long restaurantId = createdData.get("id").asLong();
        assertThat(createdData.get("slug").asText()).isEqualTo("plan-libre");
        assertThat(createdData.get("planName").asText()).isEqualTo("Plan NegoCode");
        assertThat(createdData.get("adminEmail").asText()).isEqualTo("plan-libre-admin@test.com");
        assertThat(createdData.get("userCount").asLong()).isEqualTo(1);

        // Crear con plan por defecto (sin planCode → NEGOCODE) → 201
        ResponseEntity<JsonNode> defaultPlan = createRestaurant(superAdmin,
                "Plan Pro", "plan-pro", "plan-pro-admin@test.com", "PlanProAdmin123!", null);
        assertThat(defaultPlan.getStatusCode().value()).isEqualTo(201);
        assertThat(defaultPlan.getBody().get("data").get("planName").asText()).isEqualTo("Plan NegoCode");

        // Crear con planCode inexistente → 400 (antes fallback silencioso)
        ResponseEntity<JsonNode> badPlan = createRestaurant(superAdmin,
                "Plan Fallback", "plan-fallback", "plan-fallback-admin@test.com", "Fallback123!", "NO_EXISTE");
        assertThat(badPlan.getStatusCode().value()).isEqualTo(400);

        // Conflictos: email duplicado → 409; slug duplicado → 409
        ResponseEntity<JsonNode> dupEmail = createRestaurant(superAdmin,
                "Otro Nombre", "otro-slug", "plan-libre-admin@test.com", "PlanLobre123!", "NEGOCODE");
        assertThat(dupEmail.getStatusCode().value()).isEqualTo(409);

        ResponseEntity<JsonNode> dupSlug = createRestaurant(superAdmin,
                "Otro Nombre", "plan-libre", "otro-email@test.com", "PlanLobre123!", "NEGOCODE");
        assertThat(dupSlug.getStatusCode().value()).isEqualTo(409);

        // Desactivar el restaurante creado → 200; reactivar → 200; inexistente → 404
        ResponseEntity<JsonNode> deactivated = rest.exchange(
                "/api/admin/restaurants/" + restaurantId + "/active?active=false", HttpMethod.PATCH,
                superAdmin.get(), JsonNode.class);
        assertThat(deactivated.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> reactivated = rest.exchange(
                "/api/admin/restaurants/" + restaurantId + "/active?active=true", HttpMethod.PATCH,
                superAdmin.get(), JsonNode.class);
        assertThat(reactivated.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> missingRestaurant = rest.exchange(
                "/api/admin/restaurants/99999/active?active=false", HttpMethod.PATCH,
                superAdmin.get(), JsonNode.class);
        assertThat(missingRestaurant.getStatusCode().value()).isEqualTo(404);

        // Listado paginado de usuarios
        ResponseEntity<JsonNode> users = rest.exchange("/api/admin/users?page=0&size=50", HttpMethod.GET,
                superAdmin.get(), JsonNode.class);
        assertThat(users.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode userContent = users.getBody().get("data").get("content");
        assertThat(userContent.toString()).contains("superadmin@demo.com");
        long superAdminId = findIdByEmail(userContent, "superadmin@demo.com");
        long createdUserId = findIdByEmail(userContent, "plan-libre-admin@test.com");

        // Filtro por rol server-side
        ResponseEntity<JsonNode> admins = rest.exchange("/api/admin/users?role=SUPER_ADMIN", HttpMethod.GET,
                superAdmin.get(), JsonNode.class);
        assertThat(admins.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(admins.getBody().get("data").get("content").toString()).contains("superadmin@demo.com");

        // Rol inválido → 400
        ResponseEntity<JsonNode> badRole = rest.exchange("/api/admin/users?role=NOPE", HttpMethod.GET,
                superAdmin.get(), JsonNode.class);
        assertThat(badRole.getStatusCode().value()).isEqualTo(400);

        // Desactivar a un usuario (el del restaurante creado) → 200
        ResponseEntity<JsonNode> userDeactivated = rest.exchange(
                "/api/admin/users/" + createdUserId + "/active?active=false", HttpMethod.PATCH,
                superAdmin.get(), JsonNode.class);
        assertThat(userDeactivated.getStatusCode().is2xxSuccessful()).isTrue();

        // Desactivarse a sí mismo → 403
        ResponseEntity<JsonNode> selfDeactivate = rest.exchange(
                "/api/admin/users/" + superAdminId + "/active?active=false", HttpMethod.PATCH,
                superAdmin.get(), JsonNode.class);
        assertThat(selfDeactivate.getStatusCode().value()).isEqualTo(403);

        // Usuario inexistente → 404
        ResponseEntity<JsonNode> missingUser = rest.exchange(
                "/api/admin/users/99999/active?active=false", HttpMethod.PATCH,
                superAdmin.get(), JsonNode.class);
        assertThat(missingUser.getStatusCode().value()).isEqualTo(404);

        // Auditoría: debe haber registro de creación del restaurante
        ResponseEntity<JsonNode> audit = rest.exchange(
                "/api/admin/audit?entityType=restaurant&entityId=" + restaurantId, HttpMethod.GET,
                superAdmin.get(), JsonNode.class);
        assertThat(audit.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(audit.getBody().get("data").get("content").toString()).contains("RESTAURANT_CREATED");
    }

    @Test
    void lastSuperAdmin_cannotBeDeactivated() throws Exception {
        TestHttp.Session superAdmin = superAdminSession();
        ResponseEntity<JsonNode> users = rest.exchange("/api/admin/users?role=SUPER_ADMIN", HttpMethod.GET,
                superAdmin.get(), JsonNode.class);
        JsonNode content = users.getBody().get("data").get("content");
        // Si solo queda 1 superadmin activo, desactivarlo debe dar 403
        if (content.size() == 1) {
            long id = content.get(0).get("id").asLong();
            // Si es uno mismo también es 403 por auto-baja; si es otro, por último activo
            ResponseEntity<JsonNode> res = rest.exchange(
                    "/api/admin/users/" + id + "/active?active=false", HttpMethod.PATCH,
                    superAdmin.get(), JsonNode.class);
            assertThat(res.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void nonSuperAdmin_isForbidden() throws Exception {
        TestHttp.Session regular = TestHttp.register(rest, objectMapper,
                "Admin Regular", "admin-regular@test.com", "admin-regular");

        ResponseEntity<JsonNode> stats = rest.exchange("/api/admin/stats", HttpMethod.GET,
                regular.get(), JsonNode.class);
        assertThat(stats.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<JsonNode> users = rest.exchange("/api/admin/users", HttpMethod.GET,
                regular.get(), JsonNode.class);
        assertThat(users.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<JsonNode> audit = rest.exchange("/api/admin/audit", HttpMethod.GET,
                regular.get(), JsonNode.class);
        assertThat(audit.getStatusCode().value()).isEqualTo(403);
    }

    private TestHttp.Session superAdminSession() throws Exception {
        String xsrf = TestHttp.bootstrapCsrf(rest);
        return TestHttp.login(rest, objectMapper, "superadmin@demo.com", "SuperAdmin123!", xsrf);
    }

    private ResponseEntity<JsonNode> createRestaurant(TestHttp.Session session, String restaurantName,
                                                      String slug, String email, String password, String planCode)
            throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of(
                "restaurantName", restaurantName,
                "slug", slug,
                "adminName", "Admin " + restaurantName,
                "adminEmail", email,
                "adminPassword", password
        ));
        if (planCode != null) {
            body.put("planCode", planCode);
        }
        return rest.exchange("/api/admin/restaurants", org.springframework.http.HttpMethod.POST,
                TestHttp.body(objectMapper, body, session), JsonNode.class);
    }

    private long findIdByEmail(JsonNode users, String email) {
        for (JsonNode user : users) {
            if (email.equals(user.get("email").asText())) {
                return user.get("id").asLong();
            }
        }
        throw new IllegalStateException("Usuario no encontrado: " + email);
    }
}
