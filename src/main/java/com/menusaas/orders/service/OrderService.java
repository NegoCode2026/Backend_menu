package com.menusaas.orders.service;

import com.menusaas.orders.dto.CreateManualOrderRequest;
import com.menusaas.orders.dto.CreateOrderRequest;
import com.menusaas.orders.dto.OrderResponse;
import com.menusaas.orders.dto.OrderStatsResponse;
import com.menusaas.orders.dto.UpdateOrderRequest;
import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderItem;
import com.menusaas.orders.entity.OrderStatus;
import com.menusaas.orders.entity.OrderStatusHistory;
import com.menusaas.orders.entity.OrderType;
import com.menusaas.orders.events.OrderEventPublisher;
import com.menusaas.orders.repository.OrderRepository;
import com.menusaas.orders.repository.OrderStatusHistoryRepository;
import com.menusaas.permissions.Permissions;
import com.menusaas.permissions.service.PermissionService;
import com.menusaas.products.entity.Product;
import com.menusaas.products.service.ProductService;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.service.RestaurantService;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ForbiddenException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.users.entity.Role;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pedidos: creación pública (clientes) y manual (staff), edición,
 * máquina de estados con historial, tracking público y estadísticas.
 *
 * No toca repositorios ajenos: catálogo vía ProductService/RestaurantService.
 * El inventario se descuenta tras guardar (misma transacción, serializada
 * por el lock pesimista del restaurante) y se devuelve al cancelar.
 * Los eventos al staff se publican para envío WebSocket AFTER_COMMIT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final RestaurantService restaurantService;
    private final ProductService productService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final OrderEventPublisher orderEvents;
    private final PermissionService permissions;
    private final OrderPricing pricing;

    @Transactional
    public OrderResponse createPublicOrder(String slug, CreateOrderRequest request) {
        // Lock pesimista en la fila del restaurante: dos pedidos concurrentes del
        // mismo tenant se serializan aquí, evitando que count+1 genere duplicados.
        // Se resuelve vía RestaurantService para no tocar su repositorio directamente.
        Restaurant restaurant = restaurantService.findActiveBySlugForUpdateOrThrow(slug);

        if (!restaurant.isOpen()) {
            throw new BadRequestException("El restaurante está cerrado en este momento y no puede recibir pedidos. Inténtalo más tarde.");
        }

        Long restaurantId = restaurant.getId();

        Order order = Order.builder()
                .restaurantId(restaurantId)
                .customerName(request.customerName().trim())
                .customerPhone(request.customerPhone() != null ? request.customerPhone().trim() : null)
                .tableNumber(request.tableNumber() != null ? request.tableNumber().trim() : null)
                .deliveryAddress(request.deliveryAddress() != null ? request.deliveryAddress().trim() : null)
                .trackingCode(UUID.randomUUID().toString())
                .notes(request.notes() != null ? request.notes().trim() : null)
                .orderType(request.orderType() != null ? request.orderType() : OrderType.DINE_IN)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        pricing.applyItems(order, restaurantId, request.items(), request.discountAmount(), request.tipAmount());

        // Generación de consecutivo de pedido (ej. FMIX-0001)
        long count = orderRepository.countOrdersForRestaurant(restaurantId);
        order.setOrderNumber(String.format("%s-%04d", generatePrefix(restaurant.getSlug()), count + 1));

        Order saved = orderRepository.save(order);
        deductStock(saved);
        recordStatus(saved.getId(), null, OrderStatus.PENDING);
        log.info("Nuevo pedido recibido: num={}, restaurante={}, cliente={}, total={}",
                saved.getOrderNumber(), restaurant.getSlug(), saved.getCustomerName(), saved.getTotalAmount());

        OrderResponse response = withHistory(saved);
        orderEvents.orderCreated(response);
        return response;
    }

    /** Compatibilidad para consumidores que ya usaban el DTO público. */
    @Transactional
    public OrderResponse createMine(CreateOrderRequest request) {
        return createMine(new CreateManualOrderRequest(
                request.customerName(),
                request.customerPhone(),
                request.tableNumber(),
                request.deliveryAddress(),
                request.notes(),
                request.orderType(),
                request.discountAmount(),
                request.tipAmount(),
                request.items()
        ));
    }

    @Transactional
    public OrderResponse createMine(CreateManualOrderRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Restaurant restaurant = restaurantService.findByIdForUpdateOrThrow(restaurantId);

        Order order = Order.builder()
                .restaurantId(restaurantId)
                .customerName(manualCustomerName(request))
                .customerPhone(request.customerPhone() != null ? request.customerPhone().trim() : null)
                .tableNumber(request.tableNumber() != null ? request.tableNumber().trim() : null)
                .deliveryAddress(request.deliveryAddress() != null ? request.deliveryAddress().trim() : null)
                .trackingCode(UUID.randomUUID().toString())
                .notes(request.notes() != null ? request.notes().trim() : null)
                .orderType(request.orderType() != null ? request.orderType() : OrderType.DINE_IN)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        pricing.applyItems(order, restaurantId, request.items(), request.discountAmount(), request.tipAmount());

        long count = orderRepository.countOrdersForRestaurant(restaurantId);
        order.setOrderNumber(String.format("%s-%04d", generatePrefix(restaurant.getSlug()), count + 1));

        Order saved = orderRepository.save(order);
        deductStock(saved);
        recordStatus(saved.getId(), null, OrderStatus.PENDING);
        log.info("Pedido creado por el restaurante: num={}, restauranteId={}, cliente={}, total={}",
                saved.getOrderNumber(), restaurantId, saved.getCustomerName(), saved.getTotalAmount());

        OrderResponse response = withHistory(saved);
        orderEvents.orderCreated(response);
        return response;
    }

    private String manualCustomerName(CreateManualOrderRequest request) {
        String provided = request.customerName();
        if (provided != null && !provided.trim().isEmpty()) {
            return provided.trim();
        }
        if (request.orderType() == OrderType.DELIVERY) {
            return "Domicilio";
        }
        String table = request.tableNumber();
        if (table != null && !table.trim().isEmpty()) {
            return table.trim();
        }
        return "Mostrador";
    }

    @Transactional
    public OrderResponse updateMine(Long id, UpdateOrderRequest request) {
        permissions.require(Permissions.ORDERS_EDIT);
        Order order = getMineOrder(id);
        guardEditable(order);

        if (request.customerName() != null) {
            order.setCustomerName(request.customerName().trim());
        }
        if (request.customerPhone() != null) {
            order.setCustomerPhone(request.customerPhone().trim());
        }
        if (request.tableNumber() != null) {
            order.setTableNumber(request.tableNumber().trim());
        }
        if (request.deliveryAddress() != null) {
            order.setDeliveryAddress(request.deliveryAddress().trim());
        }
        if (request.notes() != null) {
            order.setNotes(request.notes().trim());
        }
        if (request.orderType() != null) {
            order.setOrderType(request.orderType());
        }
        if (request.items() != null) {
            if (request.items().isEmpty()) {
                throw new BadRequestException("El pedido debe contener al menos un producto");
            }
            // Los ítems cambian: se devuelve el stock anterior y se descuenta el nuevo.
            restoreStock(order);
            pricing.applyItems(order, order.getRestaurantId(), request.items(), request.discountAmount(), request.tipAmount());
        }

        Order updated = orderRepository.save(order);
        deductStock(updated);
        log.info("Pedido editado: id={}, num={}", updated.getId(), updated.getOrderNumber());
        return withHistory(updated);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listMine(OrderStatus status, Instant since, Integer page, Integer size) {
        Long restaurantId = SecurityUtils.currentRestaurantId();

        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("restaurantId"), restaurantId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (since != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), since));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        List<Order> orders;

        if (page != null && size != null) {
            Page<Order> result = orderRepository.findAll(spec, PageRequest.of(page, size, sort));
            orders = result.getContent();
        } else {
            orders = orderRepository.findAll(spec, sort);
        }

        return withHistory(orders);
    }

    /** Compat: listado simple sin paginación. */
    @Transactional(readOnly = true)
    public List<OrderResponse> listMine(OrderStatus status) {
        return listMine(status, null, null, null);
    }

    @Transactional(readOnly = true)
    public OrderResponse getMine(Long id) {
        Order order = getMineOrder(id);
        return withHistory(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse trackPublicOrder(String trackingCode) {
        Order order = orderRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        return withHistory(order);
    }

    @Transactional(readOnly = true)
    public OrderStatsResponse statsMine() {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Instant todayStart = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();

        return OrderStatsResponse.from(
                orderRepository.countByRestaurantId(restaurantId),
                orderRepository.countByRestaurantIdAndStatus(restaurantId, OrderStatus.PENDING),
                orderRepository.countByRestaurantIdAndStatus(restaurantId, OrderStatus.CONFIRMED),
                orderRepository.countByRestaurantIdAndStatus(restaurantId, OrderStatus.IN_PREPARATION),
                orderRepository.countByRestaurantIdAndStatus(restaurantId, OrderStatus.READY),
                orderRepository.countByRestaurantIdAndStatus(restaurantId, OrderStatus.DELIVERED),
                orderRepository.countByRestaurantIdAndStatus(restaurantId, OrderStatus.CANCELLED),
                orderRepository.countByRestaurantIdAndCreatedAtGreaterThanEqual(restaurantId, todayStart),
                orderRepository.sumTotalSince(restaurantId, todayStart)
        );
    }

    @Transactional
    public OrderResponse updateStatusMine(Long id, OrderStatus newStatus) {
        Order order = getMineOrder(id);
        OrderStatus current = order.getStatus();

        OrderStatusMachine.requireAllowed(current, newStatus);
        permissions.require(OrderStatusMachine.permissionFor(newStatus));

        order.applyStatus(newStatus);
        Order updated = orderRepository.save(order);
        recordStatus(updated.getId(), current, newStatus);
        log.info("Estado de pedido actualizado: id={}, num={}, nuevoEstado={}", updated.getId(), updated.getOrderNumber(), newStatus);

        // Al cancelar se devuelve el stock descontado al crear el pedido.
        if (newStatus == OrderStatus.CANCELLED) {
            restoreStock(updated);
        }

        if (newStatus == OrderStatus.READY) {
            try {
                whatsAppNotificationService.sendOrderReadyNotification(updated);
            } catch (Exception e) {
                log.error("Error al notificar WhatsApp para pedido id={}: {}", updated.getId(), e.getMessage());
            }
        }

        OrderResponse response = withHistory(updated);
        orderEvents.statusChanged(response);
        return response;
    }

    @Transactional
    public boolean notifyWhatsAppMine(Long id) {
        Order order = getMineOrder(id);
        return whatsAppNotificationService.sendOrderReadyNotification(order);
    }

    /**
     * Cobra un pedido ENTREGADO: fija método de pago y momento del cobro.
     * Permiso CASH_CHARGE (caja por defecto; asignable a otros roles).
     */
    @Transactional
    public OrderResponse payMine(Long id, com.menusaas.orders.entity.PaymentMethod paymentMethod) {
        Order order = getMineOrder(id);
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("Solo se puede cobrar un pedido entregado");
        }
        permissions.require(Permissions.CASH_CHARGE);
        order.setPaymentMethod(paymentMethod);
        order.setPaidAt(java.time.Instant.now());
        Order updated = orderRepository.save(order);
        log.info("Pedido cobrado: id={}, num={}, método={}", updated.getId(), updated.getOrderNumber(), paymentMethod);
        OrderResponse response = withHistory(updated);
        // El cobro también viaja por el canal en vivo: la caja y el equipo
        // ven "cobrada" sin recargar (notificaciones opt-in del staff).
        orderEvents.statusChanged(response);
        return response;
    }

    private Order getMineOrder(Long id) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        return orderRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
    }

    private void guardEditable(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("No se puede editar un pedido cancelado");
        }
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new BadRequestException("No se puede editar un pedido que ya fue entregado");
        }
    }

    // ------------------------------------------------------------------
    // Puerta de acceso para cash/reports: pedidos entregados por rango.
    // Evita que otros módulos toquen OrderRepository directamente.
    // ------------------------------------------------------------------

    /**
     * Pedidos ENTREGADOS de un restaurante entre dos instantes (para caja y reportes).
     */
    @Transactional(readOnly = true)
    public List<Order> findDeliveredBetween(Long restaurantId, Instant from, Instant to) {
        return orderRepository.findByRestaurantIdAndStatusAndCreatedAtBetween(
                restaurantId, OrderStatus.DELIVERED, from, to);
    }

    /** Descuento de inventario con orderId ya generado (misma transacción). */
    private void deductStock(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getProductId() != null) {
                productService.deductForOrder(item.getProductId(), order.getRestaurantId(), item.getQuantity(), order.getId());
            }
        }
    }

    /** Devolución de inventario (cancelación o re-edición de ítems). */
    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getProductId() != null) {
                try {
                    productService.restoreForOrder(item.getProductId(), order.getRestaurantId(), item.getQuantity(), order.getId());
                } catch (Exception e) {
                    log.error("No se pudo devolver stock del pedido id={} producto={}: {}",
                            order.getId(), item.getProductId(), e.getMessage());
                }
            }
        }
    }

    private void recordStatus(Long orderId, OrderStatus from, OrderStatus to) {
        historyRepository.save(OrderStatusHistory.builder()
                .orderId(orderId)
                .fromStatus(from)
                .toStatus(to)
                .build());
    }

    private OrderResponse withHistory(Order order) {
        return OrderResponse.from(order,
                historyRepository.findByOrderIdOrderByChangedAtAsc(order.getId()));
    }

    private List<OrderResponse> withHistory(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> ids = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderStatusHistory>> byOrder = historyRepository
                .findByOrderIdInOrderByChangedAtAsc(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(OrderStatusHistory::getOrderId));
        return orders.stream()
                .map(o -> OrderResponse.from(o, byOrder.getOrDefault(o.getId(), List.of())))
                .toList();
    }

    private String generatePrefix(String slug) {
        String clean = slug.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (clean.length() >= 4) {
            return clean.substring(0, 4);
        }
        return (clean + "ORD").substring(0, 4);
    }
}


