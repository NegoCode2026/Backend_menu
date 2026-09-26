package com.menusaas.tables.dto;

import com.menusaas.tables.entity.RestaurantTable;

import java.time.Instant;

public record TableResponse(
        Long id,
        String number,
        int seats,
        Instant createdAt
) {
    public static TableResponse from(RestaurantTable table) {
        return new TableResponse(
                table.getId(),
                table.getNumber(),
                table.getSeats(),
                table.getCreatedAt());
    }
}
