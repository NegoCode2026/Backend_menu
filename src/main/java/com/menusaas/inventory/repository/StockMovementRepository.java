package com.menusaas.inventory.repository;

import com.menusaas.inventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    Page<StockMovement> findByRestaurantIdAndProductIdOrderByCreatedAtDesc(
            Long restaurantId, Long productId, Pageable pageable);
}
