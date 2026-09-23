package com.menusaas.realtime;

import com.menusaas.orders.dto.OrderResponse;

/**
 * Evento de pedido para el staff (meseros/caja/cocina).
 * type: CREATED | STATUS_CHANGED
 */
public record OrderEvent(
        String type,
        Long restaurantId,
        OrderResponse order
) {
}
