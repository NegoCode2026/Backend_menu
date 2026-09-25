package com.menusaas.orders.dto;

import com.menusaas.orders.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record PayOrderRequest(
        @NotNull(message = "El método de pago es obligatorio")
        PaymentMethod paymentMethod
) {
}
