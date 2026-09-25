package com.menusaas.subscriptions.controller;

import com.menusaas.shared.api.ApiResponse;
import com.menusaas.subscriptions.payment.PaymentGateway;
import com.menusaas.subscriptions.payment.WebhookParamsParser;
import com.menusaas.subscriptions.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Webhooks de pasarela de pagos. Autenticación por FIRMA criptográfica del
 * proveedor (jamás por sesión), por eso está en permitAll de seguridad.
 */
@Slf4j
@Tag(name = "Webhooks", description = "Webhooks de pasarela de pagos (firma verificada)")
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentGateway paymentGateway;
    private final SubscriptionService subscriptionService;

    @Operation(summary = "Webhook de ePayco (verifica firma SHA256 + procesa el evento)")
    @PostMapping("/epayco")
    public ApiResponse<Void> epayco(@RequestBody String body,
                                    @RequestHeader(value = "Content-Type", required = false) String contentType) {
        Map<String, String> params = WebhookParamsParser.parse(body, contentType);
        log.info("Webhook ePayco recibido con {} parámetros", params.size());

        subscriptionService.applyGatewayEvent(paymentGateway.handleWebhook(params));
        return ApiResponse.ok("Webhook procesado");
    }
}
