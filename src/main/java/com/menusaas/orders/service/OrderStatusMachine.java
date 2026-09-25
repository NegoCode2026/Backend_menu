package com.menusaas.orders.service;

import com.menusaas.orders.entity.OrderStatus;
import com.menusaas.permissions.Permissions;
import com.menusaas.shared.api.BadRequestException;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados del pedido: transiciones permitidas y permiso que exige
 * cada estado destino. Sin estado ni dependencias (lógica pura).
 */
public final class OrderStatusMachine {

    private OrderStatusMachine() {
    }

    /**
     * Máquina de transiciones de estado. Los estados terminales (CANCELLED) y
     * las transiciones fuera de esta lista se rechazan con un error 400.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED, Set.of(OrderStatus.IN_PREPARATION, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.IN_PREPARATION, Set.of(OrderStatus.READY, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.READY, Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED));
    }

    /**
     * Qué permiso exige llevar un pedido a cada estado. El front limita
     * botones; esto lo blinda en la API y es configurable por restaurante
     * (ej. darle ORDER_KITCHEN a un mesero de confianza).
     */
    private static final Map<OrderStatus, String> STATUS_PERMISSIONS = new EnumMap<>(OrderStatus.class);

    static {
        STATUS_PERMISSIONS.put(OrderStatus.CONFIRMED, Permissions.ORDER_SERVE);
        STATUS_PERMISSIONS.put(OrderStatus.IN_PREPARATION, Permissions.ORDER_KITCHEN);
        STATUS_PERMISSIONS.put(OrderStatus.READY, Permissions.ORDER_KITCHEN);
        STATUS_PERMISSIONS.put(OrderStatus.DELIVERED, Permissions.ORDER_SERVE);
        STATUS_PERMISSIONS.put(OrderStatus.CANCELLED, Permissions.ORDER_CANCEL);
    }

    /**
     * Valida la transición (idempotente si es el mismo estado).
     *
     * @throws BadRequestException si el estado actual es terminal o el salto no existe.
     */
    public static void requireAllowed(OrderStatus current, OrderStatus next) {
        if (current == OrderStatus.CANCELLED) {
            throw new BadRequestException("No se puede modificar un pedido cancelado");
        }
        if (current != next && !ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new BadRequestException("No se puede pasar el pedido de " + current + " a " + next);
        }
    }

    /**
     * Permiso que exige llevar un pedido al estado indicado.
     */
    public static String permissionFor(OrderStatus next) {
        return STATUS_PERMISSIONS.get(next);
    }
}
