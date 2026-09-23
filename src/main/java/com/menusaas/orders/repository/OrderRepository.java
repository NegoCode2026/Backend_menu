package com.menusaas.orders.repository;

import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    List<Order> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    List<Order> findByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, OrderStatus status);

    Optional<Order> findByIdAndRestaurantId(Long id, Long restaurantId);

    Optional<Order> findByTrackingCode(String trackingCode);

    java.util.List<Order> findByRestaurantIdAndStatusAndCreatedAtBetween(
            Long restaurantId, OrderStatus status,
            java.time.Instant from, java.time.Instant to);

    long countByRestaurantId(Long restaurantId);

    long countByRestaurantIdAndStatus(Long restaurantId, OrderStatus status);

    long countByRestaurantIdAndCreatedAtGreaterThanEqual(Long restaurantId, Instant since);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.restaurantId = :restaurantId")
    long countOrdersForRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.restaurantId = :restaurantId AND o.createdAt >= :since")
    BigDecimal sumTotalSince(@Param("restaurantId") Long restaurantId, @Param("since") Instant since);
}