package com.menusaas.inventory.dto;

import com.menusaas.inventory.entity.MovementReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdjustStockRequest(
        @NotNull(message = "El producto es obligatorio")
        Long productId,

        /** Existencia NUEVA absoluta (no delta). */
        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 0, message = "La cantidad no puede ser negativa")
        Integer quantity,

        MovementReason reason
) {
}
