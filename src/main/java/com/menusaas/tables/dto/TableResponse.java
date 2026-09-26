package com.menusaas.tables.dto;

import com.menusaas.tables.entity.RestaurantTable;

import java.time.Instant;
import java.util.UUID;

/**
 * Mesa con su URL de menú incluida: el frontend dibuja el QR directamente
 * desde el listado, sin llamadas extra (qr-info queda como atajo por mesa).
 */
public record TableResponse(
        Long id,
        Long restaurantId,
        String label,
        UUID code,
        String restaurantSlug,
        String menuUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static TableResponse from(RestaurantTable t, String restaurantSlug, String menuUrl) {
        return new TableResponse(
                t.getId(), t.getRestaurantId(), t.getLabel(), t.getCode(),
                restaurantSlug, menuUrl, t.getCreatedAt(), t.getUpdatedAt()
        );
    }
}
