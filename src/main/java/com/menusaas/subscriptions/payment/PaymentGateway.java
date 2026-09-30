package com.menusaas.subscriptions.payment;

import com.menusaas.subscriptions.entity.Plan;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Abstracción de pasarela de pago. Con EPAYCO_PUBLIC_KEY configurado se usa
 * ePayco Smart Checkout v2; sin claves se opera en modo manual (solo dev).
 */
public interface PaymentGateway {

    boolean isConfigured();

    /**
     * Crea una sesión de checkout y devuelve el sessionId + token para
     * inicializar el Smart Checkout en el frontend.
     */
    CheckoutSession createCheckout(Long restaurantId, Plan plan, String confirmationUrl, String responseUrl);

    /**
     * Procesa un webhook de confirmación y devuelve el evento de pago resultante.
     */
    PaymentEvent handleWebhook(java.util.Map<String, String> params);

    record CheckoutSession(String sessionId, String token) {
    }

    /**
     * Evento de pago ya verificado por la pasarela.
     *
     * @param amount importe cobrado, tal como lo reportó la pasarela. Se
     *               contrasta contra el precio del plan: null si la pasarela
     *               no lo envía, en cuyo caso no se valida.
     */
    record PaymentEvent(String type, String providerReference, Long restaurantId, String planCode,
                        Instant periodEnd, BigDecimal amount) {

        public static final String TYPE_CHECKOUT_COMPLETED = "CHECKOUT_COMPLETED";
        public static final String TYPE_SUBSCRIPTION_CANCELLED = "SUBSCRIPTION_CANCELLED";

        public PaymentEvent(String type, String providerReference, Long restaurantId, String planCode,
                            Instant periodEnd) {
            this(type, providerReference, restaurantId, planCode, periodEnd, null);
        }
    }
}