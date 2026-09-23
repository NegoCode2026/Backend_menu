package com.menusaas.inventory.dto;

import com.menusaas.inventory.entity.MovementReason;
import com.menusaas.inventory.entity.StockMovement;

import java.time.Instant;

public record StockMovementResponse(
        Long id,
        Long productId,
        Integer quantity,
        MovementReason reason,
        Long orderId,
        Instant createdAt
) {
    public static StockMovementResponse from(StockMovement m) {
        return new StockMovementResponse(
                m.getId(), m.getProductId(), m.getQuantity(),
                m.getReason(), m.getOrderId(), m.getCreatedAt()
        );
    }
}
