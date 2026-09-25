package com.menusaas.cash.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CloseCashRequest(
        @NotNull(message = "El efectivo contado es obligatorio")
        @DecimalMin(value = "0.00", message = "El efectivo no puede ser negativo")
        BigDecimal countedCash,

        String notes
) {
}
