package com.menusaas.cash;

import com.menusaas.cash.dto.CashTodayResponse;
import com.menusaas.cash.dto.CloseCashRequest;
import com.menusaas.cash.dto.CashClosingResponse;
import com.menusaas.cash.entity.CashClosing;
import com.menusaas.cash.repository.CashClosingRepository;
import com.menusaas.cash.service.CashService;
import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderStatus;
import com.menusaas.orders.entity.PaymentMethod;
import com.menusaas.orders.service.OrderService;
import com.menusaas.shared.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private CashClosingRepository closingRepository;

    private CashService service;

    @BeforeEach
    void setUp() {
        service = new CashService(orderService, closingRepository);
    }

    private Order delivered(String total, PaymentMethod method) {
        return Order.builder()
                .restaurantId(1L).orderNumber("T-1").customerName("C")
                .status(OrderStatus.DELIVERED).totalAmount(new BigDecimal(total))
                .paymentMethod(method)
                .build();
    }

    @Test
    void today_groupsByPaymentMethod() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(orderService.findDeliveredBetween(
                    eq(1L), any(), any()))
                    .thenReturn(List.of(
                            delivered("20000", PaymentMethod.CASH),
                            delivered("10000", PaymentMethod.CARD),
                            delivered("5000", null)));
            when(closingRepository.findByRestaurantIdAndBusinessDate(eq(1L), any(LocalDate.class)))
                    .thenReturn(Optional.empty());

            CashTodayResponse r = service.today();

            assertThat(r.expectedCash()).isEqualByComparingTo("20000");
            assertThat(r.expectedCard()).isEqualByComparingTo("10000");
            assertThat(r.expectedTotal()).isEqualByComparingTo("30000");
            assertThat(r.unpaidDelivered()).isEqualTo(1);
            assertThat(r.closing()).isNull();
        }
    }

    @Test
    void close_computesDifference() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(orderService.findDeliveredBetween(
                    eq(1L), any(), any()))
                    .thenReturn(List.of(delivered("20000", PaymentMethod.CASH)));
            when(closingRepository.findByRestaurantIdAndBusinessDate(eq(1L), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            when(closingRepository.save(any(CashClosing.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            CashClosingResponse r = service.closeToday(new CloseCashRequest(new BigDecimal("19500"), null));

            assertThat(r.expectedCash()).isEqualByComparingTo("20000");
            assertThat(r.countedCash()).isEqualByComparingTo("19500");
            assertThat(r.difference()).isEqualByComparingTo("-500");
        }
    }
}
