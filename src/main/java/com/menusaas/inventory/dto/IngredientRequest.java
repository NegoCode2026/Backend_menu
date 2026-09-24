package com.menusaas.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record IngredientRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 160, message = "El nombre no puede superar 160 caracteres")
        String name,

        @Size(max = 20, message = "La unidad no puede superar 20 caracteres")
        String unit,

        @DecimalMin(value = "0.00", message = "El stock no puede ser negativo")
        BigDecimal stockQuantity,

        @DecimalMin(value = "0.00", message = "El umbral no puede ser negativo")
        BigDecimal lowStockThreshold,

        @DecimalMin(value = "0.00", message = "El costo no puede ser negativo")
        BigDecimal unitCost,

        Boolean trackStock
) {
}
