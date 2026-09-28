package com.menusaas.tables.dto;

import com.menusaas.tables.entity.RestaurantTable;

import java.time.Instant;

public record TableResponse(
        Long id,
        Long restaurantId,
        String number,
        Integer seats,
        Instant createdAt
) {
    public static TableResponse from(RestaurantTable t) {
        return new TableResponse(
                t.getId(),
                t.getRestaurantId(),
                t.getNumber(),
                t.getSeats(),
                t.getCreatedAt()
        );
    }
}
