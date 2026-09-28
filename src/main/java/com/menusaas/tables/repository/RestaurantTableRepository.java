package com.menusaas.tables.repository;

import com.menusaas.tables.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    List<RestaurantTable> findByRestaurantIdOrderByIdAsc(Long restaurantId);

    Optional<RestaurantTable> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndNumber(Long restaurantId, String number);
}
