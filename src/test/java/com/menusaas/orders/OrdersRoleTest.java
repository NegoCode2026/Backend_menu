package com.menusaas.orders;

import com.menusaas.auth.security.UserPrincipal;
import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderStatus;
import com.menusaas.orders.entity.PaymentMethod;
import com.menusaas.orders.repository.OrderRepository;
import com.menusaas.orders.repository.OrderStatusHistoryRepository;
import com.menusaas.orders.service.OrderPricing;
import com.menusaas.orders.service.OrderService;
import com.menusaas.orders.service.WhatsAppNotificationService;
import com.menusaas.permissions.repository.RolePermissionRepository;
import com.menusaas.permissions.service.PermissionService;
import com.menusaas.products.service.ProductService;
import com.menusaas.orders.events.OrderEventPublisher;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.service.RestaurantService;
import com.menusaas.shared.api.ForbiddenException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.users.entity.Role;
import com.menusaas.users.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El front limita botones por rol; esto blinda la API: mesero no cancela
 * ni manda a cocina, y solo caja/admin cobra.
 */
@ExtendWith(MockitoExtension.class)
class OrdersRoleTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private RestaurantService restaurantService;
    @Mock
    private ProductService productService;
    @Mock
    private WhatsAppNotificationService whatsAppNotificationService;
    @Mock
    private OrderEventPublisher orderEvents;
    @Mock
    private RolePermissionRepository rolePermissionRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        PermissionService permissions = new PermissionService(rolePermissionRepository);
        orderService = new OrderService(orderRepository, historyRepository, restaurantService,
                productService, whatsAppNotificationService, orderEvents, permissions,
                new OrderPricing(productService));
        // Sin filas personalizadas: valen los defaults por rol.
        lenient().when(rolePermissionRepository.findByRestaurantIdAndRole(any(), any()))
                .thenReturn(List.of());
    }

    private static UserPrincipal principal(String role) {
        User user = User.builder().id(7L).name("Staff").email("staff@rest.com").active(true)
                .role(new Role(null, role, null))
                .restaurant(Restaurant.builder().id(1L).name("Rest").slug("rest").build())
                .build();
        return UserPrincipal.from(user);
    }

    private static Order order(Long id, OrderStatus status) {
        return Order.builder().id(id).restaurantId(1L).orderNumber("T-1")
                .customerName("C").status(status).totalAmount(new BigDecimal("10000"))
                .build();
    }

    @Test
    void waiter_cannotCancel() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            security.when(SecurityUtils::currentUser).thenReturn(principal(Role.WAITER));
            when(orderRepository.findByIdAndRestaurantId(1L, 1L))
                    .thenReturn(Optional.of(order(1L, OrderStatus.PENDING)));

            assertThatThrownBy(() -> orderService.updateStatusMine(1L, OrderStatus.CANCELLED))
                    .isInstanceOf(ForbiddenException.class);
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void waiter_canConfirmAndDeliver() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            security.when(SecurityUtils::currentUser).thenReturn(principal(Role.WAITER));
            when(orderRepository.findByIdAndRestaurantId(1L, 1L))
                    .thenReturn(Optional.of(order(1L, OrderStatus.PENDING)));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            var updated = orderService.updateStatusMine(1L, OrderStatus.CONFIRMED);

            assertThat(updated.status()).isEqualTo(OrderStatus.CONFIRMED);
        }
    }

    @Test
    void waiter_cannotSendToKitchen() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            security.when(SecurityUtils::currentUser).thenReturn(principal(Role.WAITER));
            when(orderRepository.findByIdAndRestaurantId(1L, 1L))
                    .thenReturn(Optional.of(order(1L, OrderStatus.CONFIRMED)));

            assertThatThrownBy(() -> orderService.updateStatusMine(1L, OrderStatus.IN_PREPARATION))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void waiter_cannotCharge_cashierCan() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            security.when(SecurityUtils::currentUser).thenReturn(principal(Role.WAITER));
            when(orderRepository.findByIdAndRestaurantId(1L, 1L))
                    .thenReturn(Optional.of(order(1L, OrderStatus.DELIVERED)));

            assertThatThrownBy(() -> orderService.payMine(1L, PaymentMethod.CASH))
                    .isInstanceOf(ForbiddenException.class);
        }
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            security.when(SecurityUtils::currentUser).thenReturn(principal(Role.CASHIER));
            when(orderRepository.findByIdAndRestaurantId(2L, 1L))
                    .thenReturn(Optional.of(order(2L, OrderStatus.DELIVERED)));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            var paid = orderService.payMine(2L, PaymentMethod.CASH);

            assertThat(paid.paymentMethod()).isEqualTo(PaymentMethod.CASH);
            assertThat(paid.paidAt()).isNotNull();
        }
    }
}
