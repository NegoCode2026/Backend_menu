package com.menusaas.orders.events;

import com.menusaas.orders.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publica eventos de dominio. El envío WebSocket ocurre en el relay de
 * realtime con AFTER_COMMIT para no avisar antes de que el pedido exista en BD.
 */
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final ApplicationEventPublisher events;

    public void orderCreated(OrderResponse order) {
        events.publishEvent(new OrderEvent("CREATED", order.restaurantId(), order));
    }

    public void statusChanged(OrderResponse order) {
        events.publishEvent(new OrderEvent("STATUS_CHANGED", order.restaurantId(), order));
    }
}
