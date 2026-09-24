package com.menusaas.admin.dto;

import com.menusaas.users.entity.User;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String name,
        String email,
        String role,
        boolean active,
        Instant createdAt,
        Long restaurantId,
        String restaurantName
) {
    public static AdminUserResponse from(User u) {
        return new AdminUserResponse(
                u.getId(),
                u.getName(),
                u.getEmail(),
                u.getRole().getName(),
                u.isActive(),
                u.getCreatedAt(),
                u.getRestaurant() != null ? u.getRestaurant().getId() : null,
                u.getRestaurant() != null ? u.getRestaurant().getName() : "Plataforma (Global)"
        );
    }
}
