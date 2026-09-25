package com.menusaas.products.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        /** Categoría opcional: null = sin categoría. */
        Long categoryId,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 160, message = "El nombre no puede superar 160 caracteres")
        String name,

        @Size(max = 2000, message = "La descripción no puede superar 2000 caracteres")
        String description,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
        BigDecimal price,

        String imageUrl,

        Boolean available,

        Integer position,

        /** Costo unitario para utilidades. Opcional, default 0. */
        @DecimalMin(value = "0.00", message = "El costo no puede ser negativo")
        BigDecimal costPrice,

        /** Existencias iniciales/actuales. Opcional. */
        @Min(value = 0, message = "El stock no puede ser negativo")
        Integer stockQuantity,

        /** Umbral de alerta de stock bajo. Opcional, default 5. */
        @Min(value = 0, message = "El umbral no puede ser negativo")
        Integer lowStockThreshold,

        /** Si true, el pedido descuenta existencias y valida disponibilidad. */
        Boolean trackStock
) {
}
