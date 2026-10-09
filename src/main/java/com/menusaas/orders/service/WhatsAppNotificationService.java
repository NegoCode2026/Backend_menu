package com.menusaas.orders.service;

import com.menusaas.orders.entity.Order;
import com.menusaas.shared.http.HttpClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
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

    /**
     * Con timeout: esta llamada se ejecuta DENTRO de la transacción que actualiza
     * el pedido. Sin read timeout, un graph.facebook.com colgado retenía la
     * conexión del pool y la transacción para siempre, y bastaban pocas
     * notificaciones simultáneas para agotar el pool y tumbar la app entera.
     */
    private final RestTemplate restTemplate;

    public WhatsAppNotificationService(
            @Value("${whatsapp.connect-timeout:PT3S}") Duration connectTimeout,
            @Value("${whatsapp.read-timeout:PT5S}") Duration readTimeout) {
        this.restTemplate = HttpClientFactory.restTemplate(connectTimeout, readTimeout);
    }

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

    /**
     * @return true solo si el mensaje se envió de verdad.
     *
     * <p>Antes el caso "no hay proveedor configurado" devolvía <b>true</b>:
     * nada salía y la API respondía "notificación enviada". Para un restaurante
     * eso es peor que un fallo, porque cree que sus clientes reciben el aviso y
     * deja de comprobarlo. Ahora se devuelve false y se avisa con un ERROR.
     */
    private boolean sendOnce(String cleanPhone, String message, Order order) {
        if ("meta".equalsIgnoreCase(provider)) {
            if (phoneNumberId.isBlank() || accessToken.isBlank()) {
                log.error("WhatsApp configurado como 'meta' pero falta PHONE_NUMBER_ID o ACCESS_TOKEN. "
                        + "No se envió nada al pedido {}", order.getOrderNumber());
                return false;
            }
            return sendMetaCloudApi(cleanPhone, message);
        }
        if ("webhook".equalsIgnoreCase(provider)) {
            if (apiUrl.isBlank()) {
                log.error("WhatsApp configurado como 'webhook' pero falta WHATSAPP_API_URL. "
                        + "No se envió nada al pedido {}", order.getOrderNumber());
                return false;
            }
            return sendWebhook(cleanPhone, message, order);
        }
        log.error("WhatsApp habilitado con provider desconocido '{}' (usa meta o webhook). "
                + "No se envió nada al pedido {}.", provider, order.getOrderNumber());
        return false;
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
