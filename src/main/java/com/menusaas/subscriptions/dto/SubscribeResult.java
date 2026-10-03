package com.menusaas.subscriptions.dto;

/**
 * Resultado de una solicitud de suscripción.
 * checkoutSessionId != null → abrir Smart Checkout en el frontend con ese
 *                              sessionId y el checkoutToken.
 * checkoutSessionId == null → la suscripción ya quedó activa (modo manual).
 *
 * @param checkoutToken token de la sesión de ePayco. Smart Checkout v2 lo exige
 *                      para abrir el checkout: sin él el SDK no puede
 *                      inicializar la sesión y el pago se queda sin poder
 *                      completarse.
 */
public record SubscribeResult(SubscriptionResponse subscription, String checkoutSessionId,
                              String checkoutToken) {

    public SubscribeResult(SubscriptionResponse subscription, String checkoutSessionId) {
        this(subscription, checkoutSessionId, null);
    }
}