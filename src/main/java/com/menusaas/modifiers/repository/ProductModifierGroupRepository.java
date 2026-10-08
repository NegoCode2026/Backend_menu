package com.menusaas.modifiers.repository;

import com.menusaas.modifiers.entity.ProductModifierGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductModifierGroupRepository extends JpaRepository<ProductModifierGroup, Long> {

    List<ProductModifierGroup> findByRestaurantIdAndProductIdOrderByPositionAsc(Long restaurantId, Long productId);

    List<ProductModifierGroup> findByRestaurantIdAndProductIdInOrderByPositionAsc(Long restaurantId, List<Long> productIds);

    void deleteByProductId(Long productId);

    boolean existsByProductIdAndGroupId(Long productId, Long groupId);
}
