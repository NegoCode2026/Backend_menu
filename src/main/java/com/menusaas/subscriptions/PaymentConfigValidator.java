package com.menusaas.subscriptions;

import com.menusaas.subscriptions.payment.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Avisa al arrancar de que la pasarela de pagos no está configurada.
 *
 * <p>Es el gemelo del caso de WhatsApp, pero más caro. Sin
 * {@code EPAYCO_PUBLIC_KEY} la app usa el modo manual: la suscripción se activa
 * al instante, sin cobrar. El restaurante ve "Plan activado correctamente", un
 * cliente queda con acceso y nadie perceptibe que no se cobró nada.
 *
 * <p>No bloquea el arranque porque en desarrollo es legítimo operar sin pagos.
 * Por eso es un ERROR y no un simple aviso: alguien tiene que enterarse.
 */
@Slf4j
@Component
public class PaymentConfigValidator {

    private final PaymentGateway paymentGateway;

    public PaymentConfigValidator(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyConfiguration() {
        if (paymentGateway.isConfigured()) {
            log.info("Pasarela de pagos configurada: las suscripciones se cobrarán de verdad.");
            return;
        }
        log.error("Pasarela de pagos NO configurada (falta EPAYCO_PUBLIC_KEY). "
                + "El sistema opera en MODO MANUAL: las suscripciones se activan SIN COBRAR. "
                + "Un restaurante se llevaría acceso de pago sin pagar nada. "
                + "Ver scripts/checkout-verify.sh.");
    }
}