package com.menusaas.orders.dto;

import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderStatus;
import com.menusaas.orders.entity.OrderStatusHistory;
import com.menusaas.orders.entity.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long restaurantId,
        String orderNumber,
        String customerName,
        String customerPhone,
        String tableNumber,
        String notes,
        OrderStatus status,
        OrderType orderType,
        BigDecimal totalAmount,
        Instant readyAt,
        Instant deliveredAt,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items,
        List<OrderStatusEvent> timeline
) {
    public static OrderResponse from(Order order) {
        return from(order, List.of());
    }

    public static OrderResponse from(Order order, List<OrderStatusHistory> history) {
        return new OrderResponse(
                order.getId(),
                order.getRestaurantId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getCustomerPhone(),
                order.getTableNumber(),
                order.getNotes(),
                order.getStatus(),
                order.getOrderType(),
                order.getTotalAmount(),
                order.getReadyAt(),
                order.getDeliveredAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getItems() != null
                        ? order.getItems().stream().map(OrderItemResponse::from).toList()
                        : List.of(),
                history.stream().map(OrderStatusEvent::from).toList()
        );
    }
}