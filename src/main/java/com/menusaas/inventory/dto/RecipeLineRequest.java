package com.menusaas.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecipeLineRequest(
        @NotNull(message = "El ingrediente es obligatorio")
        Long ingredientId,

        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0.00", message = "La cantidad debe ser mayor a cero")
        BigDecimal quantity
) {
}
