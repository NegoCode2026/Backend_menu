package com.menusaas.orders.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Comprueba al arrancar que WhatsApp está realmente configurado.
 *
 * <p>No es un capricho: las variables {@code WHATSAPP_*} no estaban documentadas
 * en ningún sitio, así que un restaurante podía activar el interruptor y no
 * tener credenciales. Resultado: la llamada al proveedor fallaba en silencio y el
 * personal creía que los clientes estaban recibiendo el aviso del pedido listo.
 *
 * <p>Arranca igual (para no tumbar la app por una notificación), pero lo dice
 * con un ERROR claro. Si prefieres que sea bloqueante, cambia el log.error por
 * una excepción.
 */
@Slf4j
@Component
public class WhatsAppConfigValidator {

    private final boolean enabled;
    private final String provider;
    private final String phoneNumberId;
    private final String accessToken;
    private final String apiUrl;

    public WhatsAppConfigValidator(
            @Value("${whatsapp.enabled:false}") boolean enabled,
            @Value("${whatsapp.provider:meta}") String provider,
            @Value("${whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${whatsapp.access-token:}") String accessToken,
            @Value("${whatsapp.api-url:https://graph.facebook.com/v19.0}") String apiUrl) {
        this.enabled = enabled;
        this.provider = provider;
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.apiUrl = apiUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyConfiguration() {
        if (!enabled) {
            log.info("WhatsApp desactivado (WHATSAPP_ENABLED=false). No se enviarán avisos.");
            return;
        }

        if ("meta".equalsIgnoreCase(provider)) {
            if (phoneNumberId.isBlank() || accessToken.isBlank()) {
                log.error("WhatsApp HABILITADO como 'meta' pero faltan WHATSAPP_PHONE_NUMBER_ID o "
                        + "WHATSAPP_ACCESS_TOKEN. Ninguna notificación se enviará. "
                        + "Obténgalos en Meta → WhatsApp → API de WhatsApp.");
                return;
            }
            log.info("WhatsApp activo vía Meta Cloud API.");
            return;
        }

        if ("webhook".equalsIgnoreCase(provider)) {
            if (apiUrl.isBlank()) {
                log.error("WhatsApp HABILITADO como 'webhook' pero falta WHATSAPP_API_URL.");
                return;
            }
            log.info("WhatsApp activo vía webhook ({})", apiUrl);
            return;
        }

        log.error("WhatsApp HABILITADO con provider='{}', que no existe. Usa 'meta' o 'webhook'. "
                + "Ninguna notificación se enviará.", provider);
    }
}