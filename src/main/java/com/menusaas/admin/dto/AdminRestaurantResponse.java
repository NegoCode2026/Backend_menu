package com.menusaas.admin.dto;

import com.menusaas.restaurants.entity.Restaurant;

import java.time.Instant;

public record AdminRestaurantResponse(
        Long id,
        String name,
        String slug,
        String logoUrl,
        String phone,
        String address,
        boolean active,
        Instant createdAt,
        long userCount,
        long productCount,
        String planName,
        String adminEmail
) {
    public static AdminRestaurantResponse from(
            Restaurant r, String logoUrl, long userCount, long productCount,
            String planName, String adminEmail) {
        return new AdminRestaurantResponse(
                r.getId(), r.getName(), r.getSlug(), logoUrl, r.getPhone(), r.getAddress(),
                r.isActive(), r.getCreatedAt(), userCount, productCount, planName, adminEmail);
    }
}
