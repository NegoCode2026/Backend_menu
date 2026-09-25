package com.menusaas.restaurants.dto;

import com.menusaas.restaurants.entity.Restaurant;

import java.time.Instant;

public record RestaurantResponse(
        Long id,
        String name,
        String slug,
        String logoUrl,
        String description,
        String phone,
        String address,
        String whatsapp,
        String instagram,
        String facebook,
        boolean active,
        boolean open,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * @param logoUrl URL ya resuelta por el llamador (firmada o null).
     */
    public static RestaurantResponse from(Restaurant r, String logoUrl) {
        return new RestaurantResponse(
                r.getId(), r.getName(), r.getSlug(), logoUrl, r.getDescription(),
                r.getPhone(), r.getAddress(), r.getWhatsapp(), r.getInstagram(), r.getFacebook(),
                r.isActive(), r.isOpen(), r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}