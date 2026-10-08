package com.menusaas.modifiers.repository;

import com.menusaas.modifiers.entity.ModifierGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModifierGroupRepository extends JpaRepository<ModifierGroup, Long> {

    List<ModifierGroup> findByRestaurantIdAndActiveTrueOrderByPositionAsc(Long restaurantId);

    Optional<ModifierGroup> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndName(Long restaurantId, String name);
}
