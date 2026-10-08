package com.menusaas.orders.dto;

import com.menusaas.modifiers.entity.OrderItemModifier;
import com.menusaas.orders.entity.OrderItem;

import java.math.BigDecimal;
import java.util.List;

public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal,
        String notes,
        /**
         * Opciones que pidió el cliente. El cocina no puede trabajar sin ellas: un
         * "término término medio" es otro plato. Se incluyen con el precio
         * congelado en el momento del pedido.
         */
        List<ModifierLine> modifiers
) {
    public static OrderItemResponse from(OrderItem item) {
        return from(item, List.of());
    }

    public static OrderItemResponse from(OrderItem item, List<OrderItemModifier> modifiers) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getSubtotal(),
                item.getNotes(),
                modifiers.stream()
                        .map(m -> new ModifierLine(m.getGroupName(), m.getModifierName(),
                                m.getPriceDelta(), m.getQuantity()))
                        .toList()
        );
    }

    /** Una opción elegida, con el precio que se cobró en su momento. */
    public record ModifierLine(String groupName, String modifierName,
                               BigDecimal priceDelta, Integer quantity) {
    }
}
