package com.menusaas.subscriptions;

import com.menusaas.config.AppProperties;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.shared.scheduling.ScheduledJobLock;
import com.menusaas.subscriptions.dto.SubscribeResult;
import com.menusaas.subscriptions.dto.SubscriptionRequest;
import com.menusaas.subscriptions.dto.SubscriptionResponse;
import com.menusaas.subscriptions.entity.Plan;
import com.menusaas.subscriptions.entity.Subscription;
import com.menusaas.subscriptions.payment.ManualPaymentGateway;
import com.menusaas.subscriptions.payment.PaymentGateway;
import com.menusaas.subscriptions.repository.PlanRepository;
import com.menusaas.subscriptions.repository.SubscriptionRepository;
import com.menusaas.subscriptions.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Ciclo de vida de suscripciones: activación (modo manual), cancelación,
 * expiración automática y activación por webhook.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    /**
     * El job de expiración pide un advisory lock; en esta unidad se simula que se
     * consigue siempre, para poder ejercitar la lógica del job.
     */
    @Mock
    private ScheduledJobLock scheduledJobLock;

    private AppProperties appProperties;
    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties(
                new AppProperties.Jwt("c2VjcmV0by1kZS1wcnVlYmEtc2VndXJvLWxvbmctZW5vdWdoLXNlY3JldA==", 15, 7),
                new AppProperties.Cors(java.util.List.of("http://localhost:4200")),
                "http://localhost:4200", "http://localhost:8080", "./uploads",
                new AppProperties.Security(false, 3600, 24), new AppProperties.Payments("", "", "", ""),
                null);
        service = new SubscriptionService(subscriptionRepository, scheduledJobLock, planRepository,
                new ManualPaymentGateway(appProperties), appProperties);
        // lenient: solo el job de expiración lo consulta.
        lenient().when(scheduledJobLock.tryAcquire(anyLong())).thenReturn(true);
    }

    private Plan plan(String code, String price) {
        return Plan.builder().id(1L).code(code).name("Plan").priceMonthly(new BigDecimal(price)).active(true).build();
    }

    @Test
    void subscribe_manualMode_activatesImmediately_forPaidPlan() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan("PRO", "29900")));
            when(subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(1L, Subscription.STATUS_PENDING))
                    .thenReturn(Optional.empty());
            when(subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of());
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
                Subscription s = inv.getArgument(0);
                s.setId(10L);
                return s;
            });
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan("PRO", "29900")));

            SubscribeResult result = service.subscribe(new SubscriptionRequest("PRO"));

            assertThat(result.checkoutSessionId()).isNull();
            SubscriptionResponse response = result.subscription();
            assertThat(response.status()).isEqualTo(Subscription.STATUS_ACTIVE);
            assertThat(response.endsAt()).isNotNull(); // plan de pago: período de 30 días
        }
    }

    @Test
    void subscribe_manualMode_freePlan_hasNoExpiration() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(planRepository.findByCode("FREE")).thenReturn(Optional.of(plan("FREE", "0")));
            when(subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(1L, Subscription.STATUS_PENDING))
                    .thenReturn(Optional.empty());
            when(subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of());
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
                Subscription s = inv.getArgument(0);
                s.setId(11L);
                return s;
            });
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan("FREE", "0")));

            SubscriptionResponse response = service.subscribe(new SubscriptionRequest("FREE")).subscription();

            assertThat(response.status()).isEqualTo(Subscription.STATUS_ACTIVE);
            assertThat(response.endsAt()).isNull();
        }
    }

    @Test
    void subscribe_unknownPlan_throws404() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(planRepository.findByCode("NOEXISTE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.subscribe(new SubscriptionRequest("NOEXISTE")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    void cancelMySubscription_marksCancelled() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            Subscription active = Subscription.builder().id(5L).restaurantId(1L).planId(1L)
                    .status(Subscription.STATUS_ACTIVE).startsAt(Instant.now()).build();
            when(subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(1L, Subscription.STATUS_ACTIVE))
                    .thenReturn(Optional.of(active));
            when(subscriptionRepository.save(any(Subscription.class))).thenReturn(active);
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan("PRO", "29900")));

            SubscriptionResponse response = service.cancelMySubscription();

            assertThat(response.status()).isEqualTo(Subscription.STATUS_CANCELLED);
            assertThat(response.endsAt()).isNotNull();
        }
    }

    @Test
    void expireDueSubscriptions_marksExpired_OnlyWhenEndsAtPassed() {
        Subscription due = Subscription.builder().id(1L).restaurantId(1L).planId(1L)
                .status(Subscription.STATUS_ACTIVE)
                .startsAt(Instant.now().minusSeconds(4000))
                .endsAt(Instant.now().minusSeconds(60))
                .build();
        Subscription future = Subscription.builder().id(2L).restaurantId(2L).planId(1L)
                .status(Subscription.STATUS_ACTIVE)
                .startsAt(Instant.now())
                .endsAt(Instant.now().plusSeconds(4000))
                .build();
        // La query ya filtra por ends_at < ahora: solo "due" es candidata.
        when(subscriptionRepository.findByStatusAndEndsAtBefore(eq(Subscription.STATUS_ACTIVE), any(Instant.class)))
                .thenReturn(List.of(due));

        service.expireDueSubscriptions();

        assertThat(due.getStatus()).isEqualTo(Subscription.STATUS_EXPIRED);
        assertThat(future.getStatus()).isEqualTo(Subscription.STATUS_ACTIVE);
        verify(subscriptionRepository, times(1)).save(due);
        verify(subscriptionRepository, never()).save(future);
    }

    @Test
    void activateFromGateway_activatesSubscription_fromWebhook() {
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan("PRO", "29900")));
        when(subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(20L);
            return s;
        });
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan("PRO", "29900")));

        SubscriptionResponse response = service.activateFromGateway(
                1L, "PRO", "sub_123", Instant.now().plusSeconds(86400));

        assertThat(response.status()).isEqualTo(Subscription.STATUS_ACTIVE);
        assertThat(response.provider()).isEqualTo(Subscription.PROVIDER_EPAYCO);
        assertThat(response.providerReference()).isEqualTo("sub_123");
        assertThat(response.endsAt()).isNotNull();
    }

    @Test
    void activateFromGateway_replayedWebhook_isIgnoredAndDoesNotCreateASecondSubscription() {
        Subscription already = Subscription.builder()
                .id(7L)
                .restaurantId(1L)
                .planId(1L)
                .status(Subscription.STATUS_ACTIVE)
                .provider(Subscription.PROVIDER_EPAYCO)
                .providerReference("sub_replay")
                .startsAt(Instant.now())
                .endsAt(Instant.now().plusSeconds(86400))
                .build();
        when(subscriptionRepository.findByProviderReference("sub_replay"))
                .thenReturn(Optional.of(already));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan("PRO", "29900")));

        SubscriptionResponse response = service.activateFromGateway(
                1L, "PRO", "sub_replay", Instant.now().plusSeconds(86400), new BigDecimal("29900"));

        // Idempotente: devuelve la suscripción ya procesada y no inserta nada nuevo.
        assertThat(response.status()).isEqualTo(Subscription.STATUS_ACTIVE);
        verify(subscriptionRepository, never()).save(any());
        verify(planRepository, never()).findByCode(any());
    }

    @Test
    void activateFromGateway_amountDoesNotMatchPlan_throwsAndDoesNotActivate() {
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan("PRO", "29900")));

        assertThatThrownBy(() -> service.activateFromGateway(
                1L, "PRO", "sub_bad", Instant.now().plusSeconds(86400), new BigDecimal("1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("importe");

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void activateFromGateway_matchingAmount_activates() {
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan("PRO", "29900")));
        when(subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(21L);
            return s;
        });
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan("PRO", "29900")));

        // Escala distinta ("29900.00" vs "29900") pero mismo importe: debe validar.
        SubscriptionResponse response = service.activateFromGateway(
                1L, "PRO", "sub_ok", Instant.now().plusSeconds(86400), new BigDecimal("29900.00"));

        assertThat(response.status()).isEqualTo(Subscription.STATUS_ACTIVE);
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    @Test
    void activateFromGateway_withoutPeriodFromGateway_setsEndsAtSoTheSubscriptionCanExpire() {
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan("PRO", "29900")));
        when(subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(22L);
            return s;
        });
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan("PRO", "29900")));

        Instant before = Instant.now();
        // ePayco no manda periodo (periodEnd = null): la suscripción quedaría
        // con ends_at NULL y el job de expiración no la cerraría nunca.
        SubscriptionResponse response = service.activateFromGateway(
                1L, "PRO", "sub_noperiod", null);

        assertThat(response.endsAt()).isNotNull();
        assertThat(response.endsAt()).isAfter(before.plusSeconds(29L * 86400));
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    @Test
    void activateFromGateway_gatewayPeriodWins_overDerivedEndsAt() {
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan("PRO", "29900")));
        when(subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(23L);
            return s;
        });
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan("PRO", "29900")));

        Instant gatewayPeriodEnd = Instant.now().plusSeconds(7L * 86400);
        SubscriptionResponse response = service.activateFromGateway(
                1L, "PRO", "sub_period", gatewayPeriodEnd);

        assertThat(response.endsAt()).isEqualTo(gatewayPeriodEnd);
    }

    @Test
    void activateFromGateway_freePlan_hasNoExpiry() {
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(plan("FREE", "0")));
        when(subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(24L);
            return s;
        });
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan("FREE", "0")));

        // Plan gratuito: sin caducidad, igual que el alta manual.
        SubscriptionResponse response = service.activateFromGateway(
                1L, "FREE", "sub_free", null);

        assertThat(response.endsAt()).isNull();
    }

    @Test
    void expireDueSubscriptions_lockedByAnotherInstance_isSkipped() {
        // Con varias réplicas, solo una debe ejecutar el cron: si otra tiene el
        // advisory lock, esta no toca ninguna suscripción.
        when(scheduledJobLock.tryAcquire(ScheduledJobLock.EXPIRE_SUBSCRIPTIONS)).thenReturn(false);

        service.expireDueSubscriptions();

        verify(subscriptionRepository, never())
                .findByStatusAndEndsAtBefore(any(), any(Instant.class));
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void subscribe_withGatewayConfigured_returnsSessionIdAndToken() throws Exception {
        // Smart Checkout v2 exige sessionId Y token para abrir el cobro. Antes
        // solo se propagaba el sessionId y el token se quedaba en el backend,
        // así que el checkout no se podía abrir y el pago no se completaba.
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan("PRO", "29900")));
        when(subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(
                1L, Subscription.STATUS_PENDING))
                .thenReturn(Optional.of(Subscription.builder()
                        .id(30L)
                        .restaurantId(1L)
                        .planId(1L)
                        .status(Subscription.STATUS_PENDING)
                        .startsAt(Instant.now())
                        .build()));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan("PRO", "29900")));

        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.createCheckout(any(), any(), any(), any()))
                .thenReturn(new PaymentGateway.CheckoutSession("ses_123", "tok_abc"));
        SubscriptionService svc = new SubscriptionService(subscriptionRepository, scheduledJobLock,
                planRepository, gateway, appProperties);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);

            SubscribeResult result = svc.subscribe(new SubscriptionRequest("PRO"));

            assertThat(result.checkoutSessionId()).isEqualTo("ses_123");
            assertThat(result.checkoutToken())
                    .as("el frontend necesita el token para abrir Smart Checkout")
                    .isEqualTo("tok_abc");
        }
    }

    @Test
    void getMySubscription_withoutActive_throws404() {        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(1L, Subscription.STATUS_ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(service::getMySubscription)
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}