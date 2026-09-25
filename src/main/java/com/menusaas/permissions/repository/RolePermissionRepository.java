package com.menusaas.permissions.repository;

import com.menusaas.permissions.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRestaurantIdAndRole(Long restaurantId, String role);

    boolean existsByRestaurantIdAndRole(Long restaurantId, String role);

    void deleteByRestaurantIdAndRole(Long restaurantId, String role);
}
