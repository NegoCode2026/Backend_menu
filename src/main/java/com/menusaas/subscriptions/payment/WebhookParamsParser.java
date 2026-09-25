package com.menusaas.subscriptions.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.shared.api.BadRequestException;
import lombok.extern.slf4j.Slf4j;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Parsea el cuerpo de un webhook de pasarela de pagos. ePayco puede enviar
 * como {@code application/x-www-form-urlencoded} o como JSON.
 *
 * No registra valores del cuerpo, solo el resultado del parseo.
 */
@Slf4j
public final class WebhookParamsParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebhookParamsParser() {
    }

    /**
     * Parsea el cuerpo del webhook a un mapa de parámetros.
     *
     * @throws BadRequestException si el cuerpo es nulo o vacío.
     */
    public static Map<String, String> parse(String body, String contentType) {
        if (body == null || body.isBlank()) {
            throw new BadRequestException("Cuerpo del webhook vacío");
        }
        Map<String, String> params = new HashMap<>();
        if (contentType != null && contentType.contains("application/json")) {
            parseJson(body, params);
        } else {
            parseForm(body, params);
        }
        return params;
    }

    private static void parseJson(String body, Map<String, String> params) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = MAPPER.readValue(body, Map.class);
            json.forEach((k, v) -> params.put(k, v != null ? v.toString() : null));
        } catch (Exception ex) {
            // Se preserva el comportamiento previo: cuerpo JSON malformado no
            // rompe el webhook, se procesa con los parámetros recuperados.
            log.error("Error parseando webhook JSON ({} caracteres recibidos)", body.length());
        }
    }

    private static void parseForm(String body, Map<String, String> params) {
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            }
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
