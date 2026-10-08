package com.menusaas.modifiers.repository;

import com.menusaas.modifiers.entity.Modifier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModifierRepository extends JpaRepository<Modifier, Long> {

    List<Modifier> findByGroupIdOrderByPositionAsc(Long groupId);

    List<Modifier> findByGroupIdAndActiveTrueOrderByPositionAsc(Long groupId);

    List<Modifier> findByRestaurantIdAndGroupIdInOrderByPositionAsc(Long restaurantId, List<Long> groupIds);

    void deleteByGroupId(Long groupId);
}
