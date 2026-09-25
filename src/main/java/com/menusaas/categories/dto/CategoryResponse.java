package com.menusaas.categories.dto;

import com.menusaas.categories.entity.Category;

import java.time.Instant;

public record CategoryResponse(
        Long id,
        Long restaurantId,
        String name,
        String description,
        int position,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static CategoryResponse from(Category c) {
        return new CategoryResponse(
                c.getId(), c.getRestaurantId(), c.getName(), c.getDescription(),
                c.getPosition(), c.isActive(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}