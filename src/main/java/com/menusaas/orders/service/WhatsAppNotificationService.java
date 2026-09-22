package com.menusaas.orders.service;

import com.menusaas.orders.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class WhatsAppNotificationService {

    @Value("${whatsapp.enabled:false}")
    private boolean enabled;

    @Value("${whatsapp.provider:meta}")
    private String provider; // "meta", "webhook", "mock"

    @Value("${whatsapp.api-url:https://graph.facebook.com/v19.0}")
    private String apiUrl;

    @Value("${whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${whatsapp.access-token:}")
    private String accessToken;

    @Value("${whatsapp.default-country-code:57}")
    private String defaultCountryCode;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean sendOrderReadyNotification(Order order) {
        if (!enabled) {
            log.info("Notificación WhatsApp desactivada en configuración.");
            return false;
        }

        String rawPhone = order.getCustomerPhone();
        if (rawPhone == null || rawPhone.isBlank()) {
            log.warn("El pedido {} de {} no tiene número de teléfono registrado.", order.getOrderNumber(), order.getCustomerName());
            return false;
        }

        String cleanPhone = rawPhone.replaceAll("\\D", "");
        if (cleanPhone.isBlank()) {
            log.warn("Número de teléfono inválido para pedido {}: {}", order.getOrderNumber(), rawPhone);
            return false;
        }

        if (cleanPhone.length() == 10 && cleanPhone.startsWith("3")) {
            cleanPhone = defaultCountryCode + cleanPhone;
        }

        String message = String.format(
            "¡Hola *%s*! 👋\n\n🎉 *¡Tu pedido %s ya está listo!* 🍽️\n📍 *Ubicación/Mesa:* %s\n\nPuedes pasar a retirarlo o ya va en camino a tu mesa.\n¡Gracias por preferirnos! 😊",
            order.getCustomerName(),
            order.getOrderNumber(),
            order.getTableNumber() != null ? order.getTableNumber() : "Recepción"
        );

        log.info("Enviando notificación WhatsApp a +{}: [{}]", cleanPhone, message);

        try {
            boolean sent = sendOnce(cleanPhone, message, order);
            if (!sent) {
                log.warn("Primer envío de WhatsApp falló para pedido {}, reintentando en 1.5s...", order.getOrderNumber());
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                sent = sendOnce(cleanPhone, message, order);
            }
            return sent;
        } catch (Exception e) {
            log.error("Error al enviar notificación de WhatsApp al teléfono +{}: {}", cleanPhone, e.getMessage(), e);
            return false;
        }
    }

    private boolean sendOnce(String cleanPhone, String message, Order order) {
        if ("meta".equalsIgnoreCase(provider) && !phoneNumberId.isBlank() && !accessToken.isBlank()) {
            return sendMetaCloudApi(cleanPhone, message);
        } else if ("webhook".equalsIgnoreCase(provider) && !apiUrl.isBlank()) {
            return sendWebhook(cleanPhone, message, order);
        } else {
            log.info("📢 [WhatsApp SERVIDOR SENT] Mensaje procesado exitosamente para +{}: \"{}\"", cleanPhone, message);
            return true;
        }
    }

    private boolean sendMetaCloudApi(String toPhone, String textBody) {
        String url = String.format("%s/%s/messages", apiUrl, phoneNumberId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        Map<String, Object> textObj = new HashMap<>();
        textObj.put("preview_url", false);
        textObj.put("body", textBody);

        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", toPhone);
        body.put("type", "text");
        body.put("text", textObj);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        log.info("Respuesta API WhatsApp Meta Cloud: status={}, body={}", response.getStatusCode(), response.getBody());
        return response.getStatusCode().is2xxSuccessful();
    }

    private boolean sendWebhook(String toPhone, String textBody, Order order) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new HashMap<>();
        payload.put("phone", toPhone);
        payload.put("message", textBody);
        payload.put("orderNumber", order.getOrderNumber());
        payload.put("customerName", order.getCustomerName());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
        return response.getStatusCode().is2xxSuccessful();
    }
}
