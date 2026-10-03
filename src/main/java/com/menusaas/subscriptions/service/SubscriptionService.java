package com.menusaas.subscriptions.service;

import com.menusaas.config.AppProperties;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.shared.scheduling.ScheduledJobLock;
import com.menusaas.subscriptions.dto.PlanResponse;
import com.menusaas.subscriptions.dto.SubscribeResult;
import com.menusaas.subscriptions.dto.SubscriptionRequest;
import com.menusaas.subscriptions.dto.SubscriptionResponse;
import com.menusaas.subscriptions.entity.Plan;
import com.menusaas.subscriptions.entity.Subscription;
import com.menusaas.subscriptions.payment.PaymentGateway;
import com.menusaas.subscriptions.repository.PlanRepository;
import com.menusaas.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Ciclo de vida de suscripciones:
 * - Suscribirse a un plan (vía pasarela ePayco, o directo en modo manual).
 * - Cancelación.
 * - Expiración automática (job por hora) cuando ends_at pasa.
 * - Activación/confirmación desde webhooks de la pasarela.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /**
     * Vigencia por defecto de un plan pagado cuando la pasarela no informa el
     * periodo (ePayco no lo envía en el webhook).
     */
    private static final long PAID_PERIOD_DAYS = 30L;

    private final SubscriptionRepository subscriptionRepository;
    private final ScheduledJobLock scheduledJobLock;
    private final PlanRepository planRepository;
    private final PaymentGateway paymentGateway;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return planRepository.findByActiveTrueOrderByPriceMonthlyAsc()
                .stream()
                .map(PlanResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getMySubscription() {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        return subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, Subscription.STATUS_ACTIVE)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("El restaurante no tiene una suscripción activa"));
    }

    /**
     * Solicitud de suscripción/cambio de plan.
     * - Con pasarela configurada: crea una sesión de ePayco Smart Checkout y deja la
     *   suscripción en PENDING; se activa cuando llega el webhook.
     * - Sin pasarela (modo manual/dev): se activa al instante.
     */
    @Transactional
    public SubscribeResult subscribe(SubscriptionRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Plan plan = planRepository.findByCode(request.planCode())
                .filter(Plan::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado"));

        Subscription pending = subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(
                        restaurantId, Subscription.STATUS_PENDING)
                .orElseGet(() -> {
                    Subscription created = Subscription.builder()
                            .restaurantId(restaurantId)
                            .planId(plan.getId())
                            .status(Subscription.STATUS_PENDING)
                            .provider(paymentGateway.isConfigured()
                                    ? Subscription.PROVIDER_EPAYCO
                                    : Subscription.PROVIDER_MANUAL)
                            .startsAt(Instant.now())
                            .build();
                    return subscriptionRepository.save(created);
                });
        pending.setPlanId(plan.getId());

        if (paymentGateway.isConfigured()) {
            String base = appProperties.appBaseUrl();
            String confirmationUrl = appProperties.apiBaseUrl() + "/api/webhooks/epayco";
            String responseUrl = base + "/admin/settings?checkout=done";
            PaymentGateway.CheckoutSession session = paymentGateway.createCheckout(
                    restaurantId, plan, confirmationUrl, responseUrl);
            pending.setProvider(Subscription.PROVIDER_EPAYCO);
            pending.setProviderReference(session.sessionId());
            subscriptionRepository.save(pending);
            return new SubscribeResult(toResponse(pending), session.sessionId(), session.token());
        }

        // Modo manual (sin pasarela): activación inmediata.
        return new SubscribeResult(activate(restaurantId, plan, Subscription.PROVIDER_MANUAL, null), null);
    }

    @Transactional
    public SubscriptionResponse cancelMySubscription() {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Subscription active = subscriptionRepository
                .findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, Subscription.STATUS_ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No hay una suscripción activa"));
        active.setStatus(Subscription.STATUS_CANCELLED);
        if (active.getEndsAt() == null) {
            active.setEndsAt(Instant.now());
        }
        return toResponse(subscriptionRepository.save(active));
    }

    /**
     * Confirma una suscripción desde la pasarela (webhook checkout.session.completed).
     * Sin validación de importe.
     */
    public SubscriptionResponse activateFromGateway(Long restaurantId, String planCode,
                                                    String providerReference, Instant periodEnd) {
        return activateFromGateway(restaurantId, planCode, providerReference, periodEnd, null);
    }

    /**
     * Confirma una suscripción desde la pasarela (webhook checkout.session.completed).
     *
     * <p>Idempotente por {@code providerReference}: la pasarela reintenta el
     * webhook y un replay del mismo {@code ref_payco} no debe volver a activar
     * (ni a cancelar la suscripción previa) ni crear un segundo registro.
     *
     * <p>Si la pasarela reporta el importe ({@code amount}), se contrasta con el
     * precio del plan: un desajuste se rechaza en vez de dar acceso por un pago
     * de otro valor.
     */
    @Transactional
    public SubscriptionResponse activateFromGateway(Long restaurantId, String planCode,
                                                    String providerReference, Instant periodEnd,
                                                    BigDecimal amount) {
        if (restaurantId == null || planCode == null) {
            throw new BadRequestException("Webhook sin datos de restaurante/plan");
        }

        // Anti-replay: si esta referencia ya se procesó, no se toca nada.
        if (providerReference != null && !providerReference.isBlank()) {
            Optional<Subscription> already = subscriptionRepository.findByProviderReference(providerReference);
            if (already.isPresent()) {
                log.info("Webhook ePayco ya procesado (ref={}), se ignora el replay", providerReference);
                return toResponse(already.get());
            }
        }

        Plan plan = planRepository.findByCode(planCode)
                .filter(Plan::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado en el webhook"));

        if (amount != null && plan.getPriceMonthly() != null
                && amount.compareTo(plan.getPriceMonthly()) != 0) {
            log.error("Importe ePayco no coincide con el plan: ref={}, plan={}, cobrado={}, esperado={}",
                    providerReference, planCode, amount, plan.getPriceMonthly());
            throw new BadRequestException("El importe del pago no corresponde al plan contratado");
        }

        // Cancela cualquier suscripción activa/pendiente previa del restaurante.
        subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(
                        restaurantId, List.of(Subscription.STATUS_ACTIVE, Subscription.STATUS_PENDING))
                .forEach(s -> s.setStatus(Subscription.STATUS_CANCELLED));

        Subscription subscription = Subscription.builder()
                .restaurantId(restaurantId)
                .planId(plan.getId())
                .status(Subscription.STATUS_ACTIVE)
                .provider(Subscription.PROVIDER_EPAYCO)
                .providerReference(providerReference)
                .startsAt(Instant.now())
                .endsAt(resolveEndsAt(plan, periodEnd))
                .build();
        return toResponse(subscriptionRepository.save(subscription));
    }

    /**
     * Fecha de fin de vigencia.
     *
     * <p>Si la pasarela manda su propio periodo se respeta. Si no lo manda
     * (ePayco no lo incluye en el webhook) se deriva del plan, porque
     * {@code ends_at} en NULL nunca cumple {@code endsAtBefore} y la
     * suscripción quedaría ACTIVE para siempre: el job de expiración no la
     * cerraría y un restaurante que deja de pagar conservaría el acceso.
     *
     * <p>Un plan gratuito (precio 0) queda sin caducidad, igual que en el
     * alta manual.
     */
    private Instant resolveEndsAt(Plan plan, Instant periodEnd) {
        if (periodEnd != null) {
            return periodEnd;
        }
        return plan.getPriceMonthly() != null && plan.getPriceMonthly().signum() > 0
                ? Instant.now().plusSeconds(PAID_PERIOD_DAYS * 86400)
                : null;
    }

    /**
     * Despacha un evento ya verificado de la pasarela hacia el caso de uso
     * correspondiente. Evento nulo o tipo desconocido = sin operación.
     */
    @Transactional
    public void applyGatewayEvent(PaymentGateway.PaymentEvent event) {
        if (event == null) {
            return;
        }
        switch (event.type()) {
            case PaymentGateway.PaymentEvent.TYPE_CHECKOUT_COMPLETED ->
                    activateFromGateway(
                            event.restaurantId(), event.planCode(), event.providerReference(),
                            event.periodEnd(), event.amount());
            case PaymentGateway.PaymentEvent.TYPE_SUBSCRIPTION_CANCELLED ->
                    cancelFromGateway(event.restaurantId(), event.providerReference());
            default -> {
            }
        }
    }

    /**
     * Procesa la cancelación reportada por la pasarela (customer.subscription.deleted).
     */
    @Transactional
    public void cancelFromGateway(Long restaurantId, String providerReference) {
        if (providerReference == null || providerReference.isBlank()) {
            return;
        }
        subscriptionRepository.findByProviderReference(providerReference)
                .ifPresent(sub -> {
                    sub.setStatus(Subscription.STATUS_CANCELLED);
                    if (sub.getEndsAt() == null) {
                        sub.setEndsAt(Instant.now());
                    }
                    subscriptionRepository.save(sub);
                });
    }

    /**
     * Expiración automática: suscripciones ACTIVE cuya fecha de fin ya pasó.
     */
    @Transactional
    @Scheduled(cron = "0 5 * * * *", zone = "UTC")
    public void expireDueSubscriptions() {
        // Con varias réplicas todas dispararían este cron y competirían por las
        // mismas suscripciones. El advisory lock hace que solo una lo ejecute.
        if (!scheduledJobLock.tryAcquire(ScheduledJobLock.EXPIRE_SUBSCRIPTIONS)) {
            log.debug("Expiración de suscripciones omitida: otra instancia la está ejecutando.");
            return;
        }
        List<Subscription> due = subscriptionRepository
                .findByStatusAndEndsAtBefore(Subscription.STATUS_ACTIVE, Instant.now());
        for (Subscription subscription : due) {
            subscription.setStatus(Subscription.STATUS_EXPIRED);
            subscriptionRepository.save(subscription);
            log.info("Suscripción expirada: id={}, restaurante={}", subscription.getId(), subscription.getRestaurantId());
        }
    }

    /**
     * Activa la suscripción (modo manual o confirmación local).
     */
    private SubscriptionResponse activate(Long restaurantId, Plan plan, String provider, String providerReference) {
        subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(
                        restaurantId, List.of(Subscription.STATUS_ACTIVE, Subscription.STATUS_PENDING))
                .forEach(s -> s.setStatus(Subscription.STATUS_CANCELLED));

        Subscription subscription = Subscription.builder()
                .restaurantId(restaurantId)
                .planId(plan.getId())
                .status(Subscription.STATUS_ACTIVE)
                .provider(provider)
                .providerReference(providerReference)
                .startsAt(Instant.now())
                .endsAt(resolveEndsAt(plan, null))
                .build();
        return toResponse(subscriptionRepository.save(subscription));
    }

    private SubscriptionResponse toResponse(Subscription s) {
        Plan plan = planRepository.findById(s.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan de la suscripción no existe"));
        return SubscriptionResponse.from(s, PlanResponse.from(plan));
    }
}