package com.menusaas.orders.dto;

import com.menusaas.orders.entity.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "El nombre del cliente es obligatorio")
        @Size(max = 120, message = "El nombre del cliente no puede superar 120 caracteres")
        String customerName,

        @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
        String customerPhone,

        @Size(max = 30, message = "El número de mesa no puede superar 30 caracteres")
        String tableNumber,

        @Size(max = 255, message = "La dirección del domicilio no puede superar 255 caracteres")
        String deliveryAddress,

        String notes,

        OrderType orderType,

        @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo")
        BigDecimal discountAmount,

        @DecimalMin(value = "0.00", message = "La propina no puede ser negativa")
        BigDecimal tipAmount,

        @NotEmpty(message = "El pedido debe contener al menos un producto")
        @Valid
        List<OrderItemRequest> items
) {
}
