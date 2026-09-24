package com.menusaas.inventory.dto;

import com.menusaas.inventory.entity.Ingredient;

import java.math.BigDecimal;
import java.time.Instant;

public record IngredientResponse(
        Long id,
        String name,
        String unit,
        BigDecimal stockQuantity,
        BigDecimal lowStockThreshold,
        boolean trackStock,
        boolean lowStock,
        Instant createdAt,
        Instant updatedAt
) {
    public static IngredientResponse from(Ingredient i) {
        return new IngredientResponse(
                i.getId(), i.getName(), i.getUnit(), i.getStockQuantity(),
                i.getLowStockThreshold(), i.isTrackStock(),
                i.isTrackStock() && i.getStockQuantity().compareTo(i.getLowStockThreshold()) <= 0,
                i.getCreatedAt(), i.getUpdatedAt()
        );
    }
}
