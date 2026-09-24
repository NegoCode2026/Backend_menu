package com.menusaas.realtime;

import com.menusaas.orders.events.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relay AFTER_COMMIT: el staff solo recibe el evento cuando el pedido
 * ya está confirmado en BD (evita "pedido fantasma" si hay rollback).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsOrderRelay {

    private final SimpMessagingTemplate messaging;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderEvent(OrderEvent event) {
        String topic = "/topic/r/" + event.restaurantId() + "/orders";
        messaging.convertAndSend(topic, event);
        log.debug("WS {} -> {} pedido {}", event.type(), topic, event.order().orderNumber());
    }
}
