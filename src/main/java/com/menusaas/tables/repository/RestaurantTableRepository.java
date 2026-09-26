package com.menusaas.tables.repository;

import com.menusaas.tables.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    List<RestaurantTable> findByRestaurantIdOrderByIdAsc(Long restaurantId);

    Optional<RestaurantTable> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndLabelIgnoreCase(Long restaurantId, String label);

    /**
     * Búsqueda global por código QR. El code es un UUID no adivinable
     * (igual que OrderRepository.findByTrackingCode): el tenant se valida
     * después en el service comparando restaurantId.
     */
    Optional<RestaurantTable> findByCode(UUID code);
}
