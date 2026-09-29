package com.menusaas.publicmenu.dto;

import com.menusaas.restaurants.entity.Restaurant;

/**
 * Tarjeta pública de un restaurante para el directorio: solo datos de
 * exhibición, nunca métricas ni datos sensibles.
 */
public record PublicRestaurantResponse(
        Long id,
        String name,
        String slug,
        String logoUrl,
        String description,
        String phone,
        String address,
        String whatsapp,
        boolean open
) {
    public static PublicRestaurantResponse from(Restaurant r, String logoUrl) {
        return new PublicRestaurantResponse(
                r.getId(), r.getName(), r.getSlug(), logoUrl, r.getDescription(),
                r.getPhone(), r.getAddress(), r.getWhatsapp(), r.isOpen());
    }
}