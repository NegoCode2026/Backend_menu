package com.menusaas.cash.service;

import com.menusaas.cash.dto.CashClosingResponse;
import com.menusaas.cash.dto.CashTodayResponse;
import com.menusaas.cash.dto.CloseCashRequest;
import com.menusaas.cash.entity.CashClosing;
import com.menusaas.cash.repository.CashClosingRepository;
import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.PaymentMethod;
import com.menusaas.orders.service.OrderService;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Cierre de caja diario: lo esperado sale de los pedidos ENTREGADOS del día
 * agrupados por método de pago; el cajero cuenta el efectivo físico y queda
 * la diferencia (faltante/sobrante) registrada con quién cerró.
 */
@Service
@RequiredArgsConstructor
public class CashService {

    private final OrderService orderService;
    private final CashClosingRepository closingRepository;

    @Transactional(readOnly = true)
    public CashTodayResponse today() {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        LocalDate date = LocalDate.now(ZoneOffset.UTC);

        List<Order> delivered = orderService.findDeliveredBetween(
                restaurantId,
                date.atStartOfDay(ZoneOffset.UTC).toInstant(),
                date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());

        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        BigDecimal transfer = BigDecimal.ZERO;
        long unpaid = 0;
        for (Order order : delivered) {
            BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            if (order.getPaymentMethod() == null) {
                unpaid++;
            } else if (order.getPaymentMethod() == PaymentMethod.CASH) {
                cash = cash.add(total);
            } else if (order.getPaymentMethod() == PaymentMethod.CARD) {
                card = card.add(total);
            } else {
                transfer = transfer.add(total);
            }
        }

        CashClosingResponse closing = closingRepository
                .findByRestaurantIdAndBusinessDate(restaurantId, date)
                .map(CashClosingResponse::from)
                .orElse(null);

        return CashTodayResponse.from(date, cash, card, transfer,
                delivered.size(), unpaid, closing);
    }

    @Transactional
    public CashClosingResponse closeToday(CloseCashRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        CashTodayResponse expected = today();

        CashClosing closing = closingRepository
                .findByRestaurantIdAndBusinessDate(restaurantId, date)
                .orElseGet(() -> CashClosing.builder()
                        .restaurantId(restaurantId)
                        .businessDate(date)
                        .build());
        closing.setExpectedCash(expected.expectedCash());
        closing.setCountedCash(request.countedCash());
        closing.setDifference(request.countedCash().subtract(expected.expectedCash()));
        closing.setNotes(request.notes() != null ? request.notes().trim() : null);
        try {
            closing.setClosedBy(SecurityUtils.currentUser().getId());
        } catch (Exception ignored) {
            // Sin contexto (nunca debería pasar con auth, pero no rompe el cierre)
        }
        return CashClosingResponse.from(closingRepository.save(closing));
    }

    @Transactional(readOnly = true)
    public Page<CashClosingResponse> history(Pageable pageable) {
        return closingRepository
                .findByRestaurantIdOrderByBusinessDateDesc(SecurityUtils.currentRestaurantId(), pageable)
                .map(CashClosingResponse::from);
    }
}
