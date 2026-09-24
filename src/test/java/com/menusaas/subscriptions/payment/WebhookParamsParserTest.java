package com.menusaas.subscriptions.payment;

import com.menusaas.shared.api.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookParamsParserTest {

    @Test
    void nullBody_throws400() {
        assertThatThrownBy(() -> WebhookParamsParser.parse(null, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void blankBody_throws400() {
        assertThatThrownBy(() -> WebhookParamsParser.parse("   ", "application/x-www-form-urlencoded"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void formBody_parsesPairs() {
        Map<String, String> params = WebhookParamsParser.parse(
                "x_ref_payco=123&x_cod_response=1&nombre=Juan+P%C3%A9rez", null);

        assertThat(params).containsEntry("x_ref_payco", "123")
                .containsEntry("x_cod_response", "1")
                .containsEntry("nombre", "Juan Pérez");
    }

    @Test
    void jsonBody_parsesValues() {
        Map<String, String> params = WebhookParamsParser.parse(
                "{\"x_ref_payco\":123,\"status\":\"ok\",\"nulo\":null}", "application/json");

        assertThat(params).containsEntry("x_ref_payco", "123")
                .containsEntry("status", "ok")
                .containsEntry("nulo", null);
    }

    @Test
    void malformedJson_returnsRecoveredParams() {
        Map<String, String> params = WebhookParamsParser.parse("{no-json", "application/json");

        assertThat(params).isEmpty();
    }
}
