package com.menusaas.restaurants.dto;

import com.menusaas.restaurants.entity.Restaurant;

/**
 * Restaurante resumido para el directorio publico (modulo Explore).
 * Solo datos que el restaurante publica; sin informacion sensible.
 */
public record DirectoryRestaurantResponse(
        Long id,
        String name,
        String slug,
        String description,
        String logoUrl,
        String phone,
        String whatsapp,
        String address,
        boolean open,
        long productCount
) {
    public static DirectoryRestaurantResponse from(Restaurant r, long productCount) {
        return new DirectoryRestaurantResponse(
                r.getId(), r.getName(), r.getSlug(), r.getDescription(), r.getLogoUrl(),
                r.getPhone(), r.getWhatsapp(), r.getAddress(), r.isOpen(), productCount);
    }
}
