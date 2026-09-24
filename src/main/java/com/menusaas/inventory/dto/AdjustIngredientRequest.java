package com.menusaas.inventory.dto;

import com.menusaas.inventory.entity.MovementReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdjustIngredientRequest(
        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0.00", message = "La cantidad no puede ser negativa")
        BigDecimal quantity,

        MovementReason reason
) {
}
