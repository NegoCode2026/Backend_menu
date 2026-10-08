package com.menusaas.subscriptions;

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
 * La suscripción tiene que *aplicarse*, no solo registrarse.
 *
 * <p>Antes: un restaurante podía quedarse EXPIRED y seguir creando productos,
 * tomando pedidos y viendo reportes indefinidamente. El mecanismo de ingreso
 * del producto no estaba conectado a nada, así que técnicamente el SaaS era
 * gratuito para siempre.
 *
 * <p>Lo que este test protege a propósito: el menú público NO se corta. Si se
 * cortara, el restaurante perdería a sus clientes en el momento en que se le
 * acaba el periodo y se llevaría el problema con el negocio.
 */
class SubscriptionEnforcementIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    private static final String VENCIDA = "rest-vencida-1";
    private static final String AL_DIA = "rest-al-dia-1";

    private TestHttp.Session register(String slug) throws Exception {
        return TestHttp.register(rest, objectMapper, "Dueño " + slug, slug + "@test.com", slug);
    }

    /** Deja la suscripción del tenant en el estado indicado. */
    private void setSubscription(String slug, String status, String endsAtIso) {
        String sql = "INSERT INTO subscriptions "
                + "(restaurant_id, plan_id, status, provider, starts_at, ends_at, created_at, updated_at) "
                + "SELECT r.id, (SELECT id FROM plans LIMIT 1), '" + status + "', 'MANUAL', "
                + "now() - interval '60 days', " + (endsAtIso == null ? "now()" : endsAtIso) + ", now(), now() "
                + "FROM restaurants r WHERE r.slug = '" + slug + "'";
        jdbc().execute(sql);
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    private ResponseEntity<JsonNode> createProduct(TestHttp.Session session) throws Exception {
        return rest.exchange("/api/products", HttpMethod.POST,
                TestHttp.body(objectMapper, (Object) Map.of(
                        "name", "Plato", "price", 10000, "available", true), session), JsonNode.class);
    }

    @Test
    void alDia_canOperate() throws Exception {
        TestHttp.Session owner = register(AL_DIA);
        setSubscription(AL_DIA, "ACTIVE", "now() + interval '20 days'");

        assertThat(createProduct(owner).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void vencidaYSinGracia_cannotWrite() throws Exception {
        TestHttp.Session owner = register(VENCIDA);
        // Terminó hace 30 días: la gracia de 7 días ya venció.
        setSubscription(VENCIDA, "EXPIRED", "now() - interval '30 days'");

        ResponseEntity<JsonNode> blocked = createProduct(owner);

        assertThat(blocked.getStatusCode().value())
                .as("una suscripción vencida debe cortar la escritura")
                .isEqualTo(402);
        assertThat(blocked.getBody().get("code").asText()).isEqualTo("SUBSCRIPTION_REQUIRED");
    }

    @Test
    void vencidaYSinGracia_stillReadsAndStillServesThePublicMenu() throws Exception {
        TestHttp.Session owner = register("rest-vencida-2");
        setSubscription("rest-vencida-2", "EXPIRED", "now() - interval '30 days'");

        // El panel se puede consultar en modo lectura: no se pierde el histórico.
        ResponseEntity<JsonNode> list = rest.exchange("/api/products", HttpMethod.GET,
                owner.get(), JsonNode.class);
        assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();

        // Y lo más importante: el menú público sigue abierto. Si se cortara, el
        // restaurante perdería a sus clientes al acabarse el periodo.
        ResponseEntity<JsonNode> menu = rest.getForEntity(
                "/api/public/menu/" + "rest-vencida-2", JsonNode.class);
        assertThat(menu.getStatusCode().is2xxSuccessful())
                .as("el menú público nunca se bloquea")
                .isTrue();
    }

    @Test
    void vencidaPeroDentroDeGracia_canStillOperate() throws Exception {
        TestHttp.Session owner = register("rest-gracia-1");
        // Terminó hace 3 días: dentro de los 7 de cortesía.
        setSubscription("rest-gracia-1", "EXPIRED", "now() - interval '3 days'");

        assertThat(createProduct(owner).getStatusCode().value())
                .as("la gracia evita un corte de golpe al vencer")
                .isEqualTo(201);
    }

    @Test
    void sinSuscripcion_delDia_no_queda_bloqueado() throws Exception {
        // Un alta recién creada que aún no ha pasado por cobro no debe quedar
        // sin poder operar: sería imposible pagar.
        TestHttp.Session owner = register("rest-sinsub-1");

        assertThat(createProduct(owner).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void puede_pagar_estando_vencido() throws Exception {
        // Si el endpoint de suscripción estuviera bloqueado, no podría renovarse.
        TestHttp.Session owner = register("rest-paga-1");
        setSubscription("rest-paga-1", "EXPIRED", "now() - interval '30 days'");

        ResponseEntity<JsonNode> mine = rest.exchange("/api/subscriptions/me",
                HttpMethod.GET, owner.get(), JsonNode.class);
        assertThat(mine.getStatusCode().is2xxSuccessful())
                .as("debe poder consultar su suscripción para pagar")
                .isTrue();
    }
}