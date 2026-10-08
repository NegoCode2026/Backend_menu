package com.menusaas.subscriptions.repository;

import com.menusaas.subscriptions.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, String status);

    /**
     * La más reciente sea cual sea su estado. Para que un restaurante con la
     * suscripción vencida pueda VER que está vencida y renovar: si solo se
     * busca la ACTIVE, receive un 404 indistinguible de "nunca tuvo una".
     */
    Optional<Subscription> findFirstByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    List<Subscription> findByRestaurantIdAndStatusInOrderByCreatedAtDesc(Long restaurantId, Collection<String> statuses);

    /**
     * Suscripciones en un estado para varios restaurantes (panel admin: evita N+1).
     * La más reciente por restaurante va primera (mismo criterio que
     * findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc).
     */
    @Query("select s from Subscription s where s.restaurantId in :ids and s.status = :status order by s.createdAt desc")
    List<Subscription> findByRestaurantIdsAndStatusOrderByCreatedAtDesc(
            @Param("ids") Collection<Long> ids, @Param("status") String status);

    List<Subscription> findByStatusAndEndsAtBefore(String status, Instant before);

    Optional<Subscription> findByProviderReference(String providerReference);

    long countByStatus(String status);
}