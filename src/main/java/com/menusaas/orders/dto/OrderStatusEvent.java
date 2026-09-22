package com.menusaas.orders.dto;

import com.menusaas.orders.entity.OrderStatus;
import com.menusaas.orders.entity.OrderStatusHistory;

import java.time.Instant;

public record OrderStatusEvent(
        Long id,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        Instant changedAt
) {
    public static OrderStatusEvent from(OrderStatusHistory entry) {
        return new OrderStatusEvent(
                entry.getId(),
                entry.getFromStatus(),
                entry.getToStatus(),
                entry.getChangedAt()
        );
    }
}