package com.menusaas.permissions.repository;

import com.menusaas.permissions.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    List<UserPermission> findByRestaurantIdAndUserId(Long restaurantId, Long userId);

    void deleteByRestaurantIdAndUserId(Long restaurantId, Long userId);
}
