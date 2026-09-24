package com.menusaas.orders.events;

import com.menusaas.orders.dto.OrderResponse;

/**
 * Evento de pedido para el staff (meseros/caja/cocina).
 * type: CREATED | STATUS_CHANGED.
 *
 * Vive en orders (dominio que lo emite); realtime solo lo transporta por
 * WebSocket. Así orders no depende de realtime y no hay ciclo entre módulos.
 */
public record OrderEvent(
        String type,
        Long restaurantId,
        OrderResponse order
) {
}
