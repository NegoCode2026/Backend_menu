package com.menusaas.subscriptions;

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
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El camino del dinero, de punta a punta, sin tocar la pasarela real.
 *
 * <p>ePayco manda el webhook, la firma se verifica contra las claves que
 * tenemos en el entorno de pruebas y la suscripción queda activa. Eso es
 * justamente lo que el cliente paga por, y hasta ahora solo estaba probado por
 * partes con el cliente HTTP simulado: nadie había comprobado que la firma
 *生产的 se validara, que la suscripción quedara activa y que el panel se
 * abriera con ella.
 *
 * <p>Lo que sí necesita la pasarela real (que ePayco acepte el login, que
 * Smart Checkout abra con el token) lo cubre {@code scripts/checkout-verify.sh}.
 */
class EpaycoWebhookFlowIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    private static final String CUSTOMER_ID = "1031111122222222";
    private static final String P_KEY = "pk_test_9876543210abcdef";

    /** Firma tal como la calcula ePayco: customerId^pKey^ref^tx^amount^currency. */
    private String firma(String refPayco, String transactionId, String amount, String currency) {
        try {
            String payload = CUSTOMER_ID + "^" + P_KEY + "^" + refPayco + "^"
                    + transactionId + "^" + amount + "^" + currency;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ResponseEntity<JsonNode> webhook(Map<String, String> params) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/webhooks/epayco", HttpMethod.POST,
                new HttpEntity<>(jsonBody(params), headers), JsonNode.class);
    }

    private String jsonBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
        }
        return sb.append('}').toString();
    }

    /** Localiza el tenant por el slug del restaurante registrado. */
    private long restaurantId(String slug) {
        return jdbc.queryForObject(
                "select id from restaurants where slug = ?", Long.class, slug);
    }

    private long planId() {
        return jdbc.queryForObject("select id from plans where active = true limit 1", Long.class);
    }

    private String priceOf(Long plan) {
        return jdbc.queryForObject(
                "select to_char(price_monthly, 'FM999999999990.00') from plans where id = ?", String.class, plan);
    }

    private Map<String, String> acceptedParams(Long restaurant, Long plan, String ref, String amount) {
        return Map.ofEntries(
                Map.entry("x_response", "Aceptada"),
                Map.entry("ref_payco", ref),
                Map.entry("x_transaction_id", "tx_" + ref),
                Map.entry("x_amount", amount),
                Map.entry("x_currency_code", "COP"),
                Map.entry("x_id_invoice", ref),
                Map.entry("x_extra1", String.valueOf(restaurant)),
                Map.entry("x_extra2", jdbc.queryForObject(
                        "select code from plans where id = ?", String.class, plan)),
                Map.entry("x_signature", firma(ref, "tx_" + ref, amount, "COP"))
        );
    }

    @Test
    void signedWebhook_activatesTheSubscription_andOpensThePanel() throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Paga Bien", "paga-bien@test.com", "paga-bien");
        long restaurant = restaurantId("paga-bien");
        long plan = planId();
        String amount = priceOf(plan);

        // Sin suscripción previa: el panel debe estar operativo (nunca se bloquea
        // un alta por no tener suscripción, o no podría pagar).
        assertThat(jdbc.queryForObject(
                "select count(*) from subscriptions where restaurant_id = ?", Long.class, restaurant))
                .isZero();

        // 1) El webhook firmado activa la suscripción.
        ResponseEntity<JsonNode> response = webhook(acceptedParams(restaurant, plan, "pay_ok_1", amount));
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        String status = jdbc.queryForObject(
                "select status from subscriptions where restaurant_id = ? order by created_at desc limit 1",
                String.class, restaurant);
        assertThat(status).isEqualTo("ACTIVE");

        // 2) Y con suscripción activa, el restaurante puede operar sin bloqueo.
        ResponseEntity<JsonNode> product = rest.exchange("/api/products", HttpMethod.POST,
                TestHttp.body(objectMapper, (Object) Map.of(
                        "name", "Plato tras pagar", "price", 10000, "available", true), owner),
                JsonNode.class);
        assertThat(product.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void replayedWebhook_doesNotCreateASecondSubscription() throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Reintenta", "reintento@test.com", "reintento");
        long restaurant = restaurantId("reintento");
        long plan = planId();
        String amount = priceOf(plan);

        webhook(acceptedParams(restaurant, plan, "pay_dup_1", amount));
        long afterFirst = jdbc.queryForObject(
                "select count(*) from subscriptions where restaurant_id = ?", Long.class, restaurant);

        // ePayco reintenta si no recibe el 200 a tiempo. El mismo ref_payco no
        // debe volver a activar ni crear un segundo registro.
        webhook(acceptedParams(restaurant, plan, "pay_dup_1", amount));
        long afterReplay = jdbc.queryForObject(
                "select count(*) from subscriptions where restaurant_id = ?", Long.class, restaurant);

        assertThat(afterFirst).isEqualTo(1);
        assertThat(afterReplay)
                .as("un replay del mismo ref_payco no puede duplicar la suscripción")
                .isEqualTo(1);
    }

    @Test
    void forgedSignature_isRejected() throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Falsifica", "falsifica@test.com", "falsifica");
        long restaurant = restaurantId("falsifica");
        long plan = planId();
        String amount = priceOf(plan);

        Map<String, String> params = new java.util.LinkedHashMap<>(
                acceptedParams(restaurant, plan, "pay_falso", amount));
        params.put("x_signature", firma("pay_falso", "tx_pay_falso", amount, "COP") + "00");

        ResponseEntity<JsonNode> response = webhook(params);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from subscriptions where restaurant_id = ?", Long.class, restaurant))
                .as("una firma manipulada no puede activar nada")
                .isZero();
    }

    @Test
    void amountLowerThanThePlan_isRejected() throws Exception {
        // Un webhook firmado (es decir, de verdad de ePayco) pero que dice que
        // cobran 1 peso cuando el plan cuesta más: no debe activar acceso.
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Paga Poco", "paga-poco@test.com", "paga-poco");
        long restaurant = restaurantId("paga-poco");
        long plan = planId();

        ResponseEntity<JsonNode> response =
                webhook(acceptedParams(restaurant, plan, "pay_barato", "1.00"));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        // No debe quedar ninguna suscripción activa: el desajuste se rechaza
        // antes de activar nada, así que tampoco hay fila que comprobar.
        assertThat(jdbc.queryForObject(
                "select count(*) from subscriptions where restaurant_id = ? and status = 'ACTIVE'",
                Long.class, restaurant))
                .as("un importe que no cuadra con el plan no activa el acceso")
                .isZero();
    }

    @Test
    void webhook_setsAnEndDate_soTheSubscriptionCanExpire() throws Exception {
        // El fallo que se descubrió en la auditoría: ends_at quedaba NULL y el
        // job de expiración no la cerraba nunca.
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Caduca", "caduca@test.com", "caduca");
        long restaurant = restaurantId("caduca");
        long plan = planId();

        webhook(acceptedParams(restaurant, plan, "pay_caduca", priceOf(plan)));

        Object endsAt = jdbc.queryForObject(
                "select ends_at from subscriptions where restaurant_id = ? limit 1",
                Object.class, restaurant);
        assertThat(endsAt)
                .as("sin ends_at la suscripción nunca expira y el panel no se corta")
                .isNotNull();
    }
}