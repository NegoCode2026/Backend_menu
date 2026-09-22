package com.menusaas.orders.dto;

import com.menusaas.orders.entity.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

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

        @NotEmpty(message = "El pedido debe contener al menos un producto")
        @Valid
        List<OrderItemRequest> items
) {
}
