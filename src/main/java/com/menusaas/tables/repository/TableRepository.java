package com.menusaas.tables.repository;

import com.menusaas.tables.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableRepository extends JpaRepository<RestaurantTable, Long> {
    List<RestaurantTable> findByRestaurantIdOrderByNumberAsc(Long restaurantId);
    Optional<RestaurantTable> findByIdAndRestaurantId(Long id, Long restaurantId);
    boolean existsByRestaurantIdAndNumberIgnoreCase(Long restaurantId, String number);
}
