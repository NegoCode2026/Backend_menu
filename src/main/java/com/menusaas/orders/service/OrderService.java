package com.menusaas.orders.service;

import com.menusaas.orders.dto.CreateManualOrderRequest;
import com.menusaas.orders.dto.CreateOrderRequest;
import com.menusaas.orders.dto.OrderResponse;
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
import com.menusaas.tables.service.TableService;
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
    private final TableService tableService;

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

        // La mesa debe existir si el restaurante tiene mesas registradas:
        // un QR manipulado (?mesa=2000) no puede colar pedidos a mesas fantasmas.
        if (order.getOrderType() == OrderType.DINE_IN
                && order.getTableNumber() != null && !order.getTableNumber().isBlank()) {
            java.util.List<String> knownTables = tableService.findNumbersByRestaurantId(restaurantId);
            if (!knownTables.isEmpty()
                    && !TableService.matchesKnownTable(knownTables, order.getTableNumber())) {
                throw new BadRequestException("La mesa indicada no existe en este restaurante");
            }
        }

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

    /**
     * Tope de pedidos que devuelve el listado sin paginar de forma explícita.
     * Antes devolvía TODOS los pedidos históricos del restaurante y, por el N+1
     * de items, eso eran 2+N queries. El corte es alto a propósito para no
     * esconder pedidos en uso en un restaurante, pero acotado: si se alcanza, se
     * avisa en el log para que el salto sea visible y no silencioso.
     */
    static final int DEFAULT_LIST_SIZE = 200;
    static final int MAX_LIST_SIZE = 500;

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

        // created_at puede repetir (varios pedidos en el mismo instante), así que
        // se desempata por id: sin el, LIMIT/OFFSET puede repetir o saltar filas
        // entre páginas, visible para el usuario en un listado que cambia solo.
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        List<Order> orders;

        int effectivePage = page != null && page >= 0 ? page : 0;
        int requested = size != null && size > 0 ? size : DEFAULT_LIST_SIZE;
        int effectiveSize = Math.min(requested, MAX_LIST_SIZE);

        Page<Order> result = orderRepository.findAll(spec, PageRequest.of(effectivePage, effectiveSize, sort));
        orders = result.getContent();

        // Corte observable: si la página está llena puede haber más pedidos que
        // el cliente no está viendo, y eso debe poder detectarse en producción.
        if (result.getTotalElements() > (long) effectivePage * effectiveSize + orders.size()) {
            log.warn("Listado de pedidos truncado: {}+ pedidos no devueltos (página={}, size={}, total={}). "
                            + "El panel de pedidos necesita paginación real.",
                    result.getTotalElements() - ((long) effectivePage * effectiveSize + orders.size()),
                    effectivePage, effectiveSize, result.getTotalElements());
        }

        return withHistory(orders);
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


