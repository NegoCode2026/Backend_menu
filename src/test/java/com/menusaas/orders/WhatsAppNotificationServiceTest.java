package com.menusaas.orders;

import com.menusaas.orders.entity.Order;
import com.menusaas.orders.service.WhatsAppNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WhatsApp no puede reportar un envío que no ocurrió.
 *
 * <p>Antes, cuando no había proveedor configurado, {@code sendOnce} devolvía
 * true sin hacer nada. Para un restaurante eso es peor que un fallo: el personal
 * veía "notificación enviada" y dejaba de comprobar si a sus clientes les llegaba el
 * aviso del pedido listo.
 */
class WhatsAppNotificationServiceTest {

    private WhatsAppNotificationService service(boolean enabled, String provider,
                                                String phoneNumberId, String accessToken, String apiUrl) {
        var s = new WhatsAppNotificationService(java.time.Duration.ofMillis(50),
                java.time.Duration.ofMillis(50));
        ReflectionTestUtils.setField(s, "enabled", enabled);
        ReflectionTestUtils.setField(s, "provider", provider);
        ReflectionTestUtils.setField(s, "phoneNumberId", phoneNumberId);
        ReflectionTestUtils.setField(s, "accessToken", accessToken);
        ReflectionTestUtils.setField(s, "apiUrl", apiUrl);
        return s;
    }

    private Order order() {
        return Order.builder()
                .orderNumber("ABC-0001")
                .customerName("Cliente")
                .customerPhone("3001234567")
                .build();
    }

    @Test
    void disabled_returnsFalse() {
        var s = service(false, "meta", "", "", "https://graph.facebook.com/v19.0");
        assertThat(s.sendOrderReadyNotification(order())).isFalse();
    }

    @Test
    void missingPhone_returnsFalse_withoutSending() {
        var s = service(true, "meta", "", "", "https://graph.facebook.com/v19.0");
        assertThat(s.sendOrderReadyNotification(order()))
                .as("sin teléfono no se envía nada")
                .isFalse();
    }

    @Test
    void metaConfiguredWithoutCredentials_returnsFalse_notSuccess() {
        // El bug concreto: provider=meta pero sin credenciales.
        var s = service(true, "meta", "", "", "https://graph.facebook.com/v19.0");
        assertThat(s.sendOrderReadyNotification(order()))
                .as("provider meta sin PHONE_NUMBER_ID/ACCESS_TOKEN no puede devolver true")
                .isFalse();
    }

    @Test
    void unknownProvider_returnsFalse() {
        var s = service(true, "none", "123", "token", "https://example.test");
        assertThat(s.sendOrderReadyNotification(order()))
                .as("un provider desconocido no puede fingir que envió")
                .isFalse();
    }

    @Test
    void webhookWithoutUrl_returnsFalse() {
        var s = service(true, "webhook", "", "", "");
        assertThat(s.sendOrderReadyNotification(order())).isFalse();
    }
}