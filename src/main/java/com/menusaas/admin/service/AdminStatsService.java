package com.menusaas.admin.service;

import com.menusaas.admin.dto.AdminStatsResponse;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.restaurants.repository.RestaurantRepository;
import com.menusaas.subscriptions.entity.Subscription;
import com.menusaas.subscriptions.repository.SubscriptionRepository;
import com.menusaas.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Métricas globales de plataforma. Solo lectura.
 * Excepción legítima al acceso cross-módulo: el panel SUPER_ADMIN
 * necesita agregados de todos los tenants.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Cacheable(value = "adminStats", unless = "#result == null")
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        return new AdminStatsResponse(
                restaurantRepository.count(),
                restaurantRepository.countByActive(true),
                userRepository.count(),
                subscriptionRepository.countByStatus(Subscription.STATUS_ACTIVE),
                productRepository.count()
        );
    }
}
