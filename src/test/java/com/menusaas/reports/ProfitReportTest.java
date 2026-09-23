package com.menusaas.reports;

import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderItem;
import com.menusaas.orders.entity.OrderStatus;
import com.menusaas.orders.repository.OrderRepository;
import com.menusaas.reports.dto.ProfitsResponse;
import com.menusaas.reports.service.ProfitReportService;
import com.menusaas.shared.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfitReportTest {

    @Mock
    private OrderRepository orderRepository;

    private ProfitReportService service;

    @BeforeEach
    void setUp() {
        service = new ProfitReportService(orderRepository);
    }

    private Order delivered(long id, LocalDate day, String total, String unitCost, int qty) throws Exception {
        Order order = Order.builder()
                .restaurantId(1L).orderNumber("T-1").customerName("C")
                .status(OrderStatus.DELIVERED).totalAmount(new BigDecimal(total))
                .build();
        OrderItem item = OrderItem.builder()
                .productId(1L).productName("P").unitPrice(new BigDecimal(total))
                .unitCost(new BigDecimal(unitCost)).quantity(qty)
                .subtotal(new BigDecimal(total)).build();
        order.addItem(item);
        // createdAt es @CreationTimestamp: se fija por reflexión para el test
        Field createdAt = Order.class.getDeclaredField("createdAt");
        createdAt.setAccessible(true);
        createdAt.set(order, day.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600));
        return order;
    }

    @Test
    void day_sumsRevenueMinusCost() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(orderRepository.findByRestaurantIdAndStatusAndCreatedAtBetween(
                    eq(1L), eq(OrderStatus.DELIVERED), any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(
                            delivered(1L, today, "20000", "8000", 2),
                            delivered(2L, today, "10000", "3000", 1)));

            ProfitsResponse r = service.getProfits("day", today);

            assertThat(r.revenue()).isEqualByComparingTo("30000");
            assertThat(r.cost()).isEqualByComparingTo("19000");
            assertThat(r.profit()).isEqualByComparingTo("11000");
            assertThat(r.orders()).isEqualTo(2);
            assertThat(r.days()).hasSize(1);
        }
    }

    @Test
    void week_returnsSevenDays() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(orderRepository.findByRestaurantIdAndStatusAndCreatedAtBetween(
                    eq(1L), eq(OrderStatus.DELIVERED), any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(delivered(1L, today, "10000", "4000", 1)));

            ProfitsResponse r = service.getProfits("week", today);

            assertThat(r.days()).hasSize(7);
            assertThat(r.profit()).isEqualByComparingTo("6000");
        }
    }
}
