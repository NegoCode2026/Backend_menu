package com.menusaas.subscriptions.service;

import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.subscriptions.entity.Subscription;
import com.menusaas.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Decide si un tenant puede seguir usando el panel.
 *
 * <p><b>Por qué existe.</b> La suscripción se registraba pero no se aplicaba:
 * un restaurante podía registrarse, quedarse EXPIRED y seguir creando productos,
 * tomando pedidos y viendo reportes indefinidamente. El mecanismo de ingreso
 * del producto no estaba conectado a nada.
 *
 * <p><b>Qué NO se bloquea, a propósito.</b> El menú público sigue abierto con una
 * suscripción vencida: si no, el restaurante pierde a sus clientes en el momento
 * en que se le acaba el periodo, y se lleva la mala experiencia con el negocio.
 * Tampoco se bloquean login, cobros ni los endpoints de suscripción, porque son
 * justamente los que necesita para volver a pagar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionAccessService {

    /** Días de cortesía tras caducar antes de cortar de verdad. */
    public static final int GRACE_DAYS = 7;

    private final SubscriptionRepository subscriptionRepository;

    /**
     * @return true si el tenant puede operar; false si debe pagar.
     */
    @Transactional(readOnly = true)
    public boolean canOperate() {
        Long restaurantId = currentRestaurantIdOrNull();
        if (restaurantId == null) {
            return true; // SUPER_ADMIN y contextos sin tenant
        }

        Optional<Subscription> active = subscriptionRepository
                .findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, Subscription.STATUS_ACTIVE);
        if (active.isPresent() && !isExpired(active.get())) {
            return true;
        }

        // Caducada: la gracia se mide desde ends_at, no desde el cambio de estado,
        // para que un EXPIRED tardío no corte el acceso de golpe.
        return withinGracePeriod(restaurantId);
    }

    private boolean withinGracePeriod(Long restaurantId) {
        Optional<Subscription> latest = latestSubscription(restaurantId);
        if (latest.isEmpty() || latest.get().getEndsAt() == null) {
            // Sin suscripción o sin fecha de fin: se permite (un alta que aún no
            // ha pasado por cobro no debe quedar bloqueada).
            return true;
        }
        Instant deadline = latest.get().getEndsAt().plus(Duration.ofDays(GRACE_DAYS));
        boolean inGrace = Instant.now().isBefore(deadline);
        if (!inGrace) {
            log.info("Acceso cortado por suscripción vencida: restaurante={}, endsAt={}, gracia={} días",
                    restaurantId, latest.get().getEndsAt(), GRACE_DAYS);
        }
        return inGrace;
    }

    private Optional<Subscription> latestSubscription(Long restaurantId) {
        for (String status : new String[]{Subscription.STATUS_ACTIVE, Subscription.STATUS_EXPIRED,
                Subscription.STATUS_CANCELLED, Subscription.STATUS_PENDING}) {
            Optional<Subscription> found = subscriptionRepository
                    .findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, status);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private boolean isExpired(Subscription subscription) {
        return subscription.getEndsAt() != null
                && subscription.getEndsAt().isBefore(Instant.now());
    }

    private Long currentRestaurantIdOrNull() {
        try {
            return SecurityUtils.currentRestaurantId();
        } catch (Exception e) {
            // Sin principal de restaurante (SUPER_ADMIN, login, webhook).
            return null;
        }
    }
}