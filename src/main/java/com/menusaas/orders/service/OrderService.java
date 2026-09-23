package com.menusaas.orders.service;

import com.menusaas.orders.dto.*;
import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderItem;
import com.menusaas.orders.entity.OrderStatus;
import com.menusaas.orders.repository.OrderRepository;
import com.menusaas.products.entity.Product;
import com.menusaas.products.service.ProductService;
import com.menusaas.realtime.OrderEventPublisher;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.service.RestaurantService;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestaurantService restaurantService;
    private final ProductService productService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final OrderEventPublisher orderEvents;

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
                .notes(request.notes() != null ? request.notes().trim() : null)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.items()) {
            final Product product;
            try {
                product = productService.getByIdAndRestaurantIdOrThrow(itemReq.productId(), restaurantId);
            } catch (ResourceNotFoundException e) {
                // Contrato público: producto ajeno/inexistente es 400, no 404
                // (el pedido aún no existe; es error del payload).
                throw new BadRequestException("Producto no disponible en el menú: ID " + itemReq.productId());
            }
            if (!product.isAvailable()) {
                throw new BadRequestException("El producto '" + product.getName() + "' no se encuentra disponible actualmente");
            }
            // Defensa extra: el producto debe pertenecer al tenant del pedido.
            if (!restaurantId.equals(product.getRestaurantId())) {
                throw new BadRequestException("Producto no disponible en el menú: ID " + itemReq.productId());
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.quantity()));
            total = total.add(subtotal);

            OrderItem item = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .unitPrice(unitPrice)
                    .unitCost(product.getCostPrice() != null
                            ? product.getCostPrice() : java.math.BigDecimal.ZERO)
                    .quantity(itemReq.quantity())
                    .subtotal(subtotal)
                    .notes(itemReq.notes() != null ? itemReq.notes().trim() : null)
                    .build();

            order.addItem(item);
        }

        order.setTotalAmount(total);

        // Generación de consecutivo de pedido (ej. FMIX-0001)
        long count = orderRepository.countOrdersForRestaurant(restaurantId);
        String prefix = generatePrefix(restaurant.getSlug());
        order.setOrderNumber(String.format("%s-%04d", prefix, count + 1));

        Order saved = orderRepository.save(order);

        // Descuento de inventario con el orderId ya generado (misma
        // transacción: si no hay stock, todo hace rollback). Serializado
        // por el lock pesimista del restaurante.
        for (OrderItem savedItem : saved.getItems()) {
            if (savedItem.getProductId() != null) {
                productService.deductStock(
                        savedItem.getProductId(), restaurantId, savedItem.getQuantity(), saved.getId());
            }
        }

        log.info("Nuevo pedido recibido: num={}, restaurante={}, cliente={}, total={}",
                saved.getOrderNumber(), restaurant.getSlug(), saved.getCustomerName(), saved.getTotalAmount());

        OrderResponse response = OrderResponse.from(saved);
        orderEvents.orderCreated(response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listMine(OrderStatus status) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        List<Order> orders = (status != null)
                ? orderRepository.findByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, status)
                : orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);

        return orders.stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getMine(Long id) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Order order = orderRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateStatusMine(Long id, OrderStatus newStatus) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Order order = orderRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("No se puede modificar un pedido cancelado");
        }
        if (order.getStatus() == OrderStatus.DELIVERED && newStatus != OrderStatus.DELIVERED) {
            throw new BadRequestException("No se puede modificar un pedido que ya fue entregado");
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.save(order);
        log.info("Estado de pedido actualizado: id={}, num={}, nuevoEstado={}", updated.getId(), updated.getOrderNumber(), newStatus);

        // Al cancelar se devuelve el stock descontado al crear el pedido.
        if (newStatus == OrderStatus.CANCELLED) {
            for (OrderItem item : updated.getItems()) {
                if (item.getProductId() != null) {
                    try {
                        productService.restoreStock(
                                item.getProductId(), restaurantId, item.getQuantity(), updated.getId());
                    } catch (Exception e) {
                        log.error("No se pudo devolver stock del pedido id={} producto={}: {}",
                                updated.getId(), item.getProductId(), e.getMessage());
                    }
                }
            }
        }

        if (newStatus == OrderStatus.DELIVERED) {
            try {
                whatsAppNotificationService.sendOrderReadyNotification(updated);
            } catch (Exception e) {
                log.error("Error al notificar WhatsApp para pedido id={}: {}", updated.getId(), e.getMessage());
            }
        }

        OrderResponse response = OrderResponse.from(updated);
        orderEvents.statusChanged(response);
        return response;
    }

    @Transactional
    public boolean notifyWhatsAppMine(Long id) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Order order = orderRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        return whatsAppNotificationService.sendOrderReadyNotification(order);
    }

    private String generatePrefix(String slug) {
        String clean = slug.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (clean.length() >= 4) {
            return clean.substring(0, 4);
        }
        return (clean + "ORD").substring(0, 4);
    }
}
