package com.menusaas.inventory.dto;

import com.menusaas.inventory.entity.MovementReason;
import com.menusaas.inventory.entity.StockMovement;

import java.math.BigDecimal;
import java.time.Instant;

public record StockMovementResponse(
        Long id,
        Long productId,
        Long ingredientId,
        BigDecimal quantity,
        MovementReason reason,
        Long orderId,
        Instant createdAt
) {
    public static StockMovementResponse from(StockMovement m) {
        return new StockMovementResponse(
                m.getId(), m.getProductId(), m.getIngredientId(), m.getQuantity(),
                m.getReason(), m.getOrderId(), m.getCreatedAt()
        );
    }
}
