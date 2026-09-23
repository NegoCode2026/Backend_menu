package com.menusaas.orders.service;

import com.menusaas.orders.dto.*;
import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderItem;
import com.menusaas.orders.entity.OrderStatus;
import com.menusaas.orders.entity.OrderStatusHistory;
import com.menusaas.orders.entity.OrderType;
import com.menusaas.orders.repository.OrderRepository;
import com.menusaas.orders.repository.OrderStatusHistoryRepository;
import com.menusaas.products.entity.Product;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.repository.RestaurantRepository;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final ProductRepository productRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;

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

    @Transactional
    public OrderResponse createPublicOrder(String slug, CreateOrderRequest request) {
        // Lock pesimista en la fila del restaurante: dos pedidos concurrentes del
        // mismo tenant se serializan aquí, evitando que count+1 genere duplicados.
        Restaurant restaurant = restaurantRepository.findBySlugForUpdate(slug.trim().toLowerCase())
                .filter(Restaurant::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("El menú digital no existe o no está disponible"));

        if (!restaurant.isOpen()) {
            throw new BadRequestException("El restaurante está cerrado en este momento y no puede recibir pedidos. Inténtalo más tarde.");
        }

        Order order = Order.builder()
                .restaurantId(restaurant.getId())
                .customerName(request.customerName().trim())
                .customerPhone(request.customerPhone() != null ? request.customerPhone().trim() : null)
                .tableNumber(request.tableNumber() != null ? request.tableNumber().trim() : null)
                .deliveryAddress(request.deliveryAddress() != null ? request.deliveryAddress().trim() : null)
                .trackingCode(java.util.UUID.randomUUID().toString())
                .notes(request.notes() != null ? request.notes().trim() : null)
                .orderType(request.orderType() != null ? request.orderType() : OrderType.DINE_IN)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        applyItems(order, restaurant.getId(), request.items());

        // Generación de consecutivo de pedido (ej. FMIX-0001)
        long count = orderRepository.countOrdersForRestaurant(restaurant.getId());
        order.setOrderNumber(String.format("%s-%04d", generatePrefix(restaurant.getSlug()), count + 1));

        Order saved = orderRepository.save(order);
        recordStatus(saved.getId(), null, OrderStatus.PENDING);
        log.info("Nuevo pedido recibido: num={}, restaurante={}, cliente={}, total={}",
                saved.getOrderNumber(), restaurant.getSlug(), saved.getCustomerName(), saved.getTotalAmount());

        return withHistory(saved);
    }

    @Transactional
    public OrderResponse createMine(CreateOrderRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Restaurant restaurant = restaurantRepository.findByIdForUpdate(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));

        Order order = Order.builder()
                .restaurantId(restaurantId)
                .customerName(request.customerName().trim())
                .customerPhone(request.customerPhone() != null ? request.customerPhone().trim() : null)
                .tableNumber(request.tableNumber() != null ? request.tableNumber().trim() : null)
                .deliveryAddress(request.deliveryAddress() != null ? request.deliveryAddress().trim() : null)
                .notes(request.notes() != null ? request.notes().trim() : null)
                .orderType(request.orderType() != null ? request.orderType() : OrderType.DINE_IN)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        applyItems(order, restaurantId, request.items());

        long count = orderRepository.countOrdersForRestaurant(restaurantId);
        order.setOrderNumber(String.format("%s-%04d", generatePrefix(restaurant.getSlug()), count + 1));

        Order saved = orderRepository.save(order);
        recordStatus(saved.getId(), null, OrderStatus.PENDING);
        log.info("Pedido creado por el restaurante: num={}, restauranteId={}, cliente={}, total={}",
                saved.getOrderNumber(), restaurantId, saved.getCustomerName(), saved.getTotalAmount());

        return withHistory(saved);
    }

    @Transactional
    public OrderResponse updateMine(Long id, UpdateOrderRequest request) {
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
            applyItems(order, order.getRestaurantId(), request.items());
        }

        Order updated = orderRepository.save(order);
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

        return new OrderStatsResponse(
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

        if (current == OrderStatus.CANCELLED) {
            throw new BadRequestException("No se puede modificar un pedido cancelado");
        }
        if (current != newStatus && !ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(newStatus)) {
            throw new BadRequestException("No se puede pasar el pedido de " + current + " a " + newStatus);
        }

        order.applyStatus(newStatus);
        Order updated = orderRepository.save(order);
        recordStatus(updated.getId(), current, newStatus);
        log.info("Estado de pedido actualizado: id={}, num={}, nuevoEstado={}", updated.getId(), updated.getOrderNumber(), newStatus);

        if (newStatus == OrderStatus.READY) {
            try {
                whatsAppNotificationService.sendOrderReadyNotification(updated);
            } catch (Exception e) {
                log.error("Error al notificar WhatsApp para pedido id={}: {}", updated.getId(), e.getMessage());
            }
        }

        return withHistory(updated);
    }

    @Transactional
    public boolean notifyWhatsAppMine(Long id) {
        Order order = getMineOrder(id);
        return whatsAppNotificationService.sendOrderReadyNotification(order);
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

    private void applyItems(Order order, Long restaurantId, List<OrderItemRequest> itemRequests) {
        order.getItems().clear();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : itemRequests) {
            Product product = productRepository.findByIdAndRestaurantId(itemReq.productId(), restaurantId)
                    .orElseThrow(() -> new BadRequestException("Producto no disponible en el menú: ID " + itemReq.productId()));

            if (!product.isAvailable()) {
                throw new BadRequestException("El producto '" + product.getName() + "' no se encuentra disponible actualmente");
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.quantity()));
            total = total.add(subtotal);

            OrderItem item = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .unitPrice(unitPrice)
                    .quantity(itemReq.quantity())
                    .subtotal(subtotal)
                    .notes(itemReq.notes() != null ? itemReq.notes().trim() : null)
                    .build();

            order.addItem(item);
        }

        order.setTotalAmount(total);
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
        Map<Long, List<com.menusaas.orders.entity.OrderStatusHistory>> byOrder = historyRepository
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