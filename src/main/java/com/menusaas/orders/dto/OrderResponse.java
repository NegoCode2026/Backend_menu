package com.menusaas.orders.dto;

import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderStatus;
import com.menusaas.orders.entity.OrderStatusHistory;
import com.menusaas.orders.entity.OrderType;
import com.menusaas.orders.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long restaurantId,
        String orderNumber,
        String trackingCode,
        String customerName,
        String customerPhone,
        String tableNumber,
        String deliveryAddress,
        String notes,
        OrderStatus status,
        OrderType orderType,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        Instant paidAt,
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
                order.getTrackingCode(),
                order.getCustomerName(),
                order.getCustomerPhone(),
                order.getTableNumber(),
                order.getDeliveryAddress(),
                order.getNotes(),
                order.getStatus(),
                order.getOrderType(),
                order.getTotalAmount(),
                order.getPaymentMethod(),
                order.getPaidAt(),
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