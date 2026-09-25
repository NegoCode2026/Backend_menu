package com.menusaas.reports.service;

import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderItem;
import com.menusaas.orders.service.OrderService;
import com.menusaas.reports.dto.ProfitsResponse;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Utilidades por pedidos ENTREGADOS (ingreso - costo snapshot).
 * period: day (1 día) | week (7 días) | month (mes calendario).
 * date: fecha de referencia (default hoy), zona UTC.
 */
@Service
@RequiredArgsConstructor
public class ProfitReportService {

    private final OrderService orderService;

    @Transactional(readOnly = true)
    public ProfitsResponse getProfits(String period, LocalDate date) {
        String normalized = period == null ? "day" : period.trim().toLowerCase();
        if (!List.of("day", "week", "month").contains(normalized)) {
            throw new BadRequestException("period debe ser day|week|month");
        }
        LocalDate ref = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        LocalDate from;
        LocalDate to;
        switch (normalized) {
            case "week" -> {
                from = ref.minusDays(6);
                to = ref;
            }
            case "month" -> {
                from = ref.withDayOfMonth(1);
                to = ref.withDayOfMonth(ref.lengthOfMonth());
            }
            default -> {
                from = ref;
                to = ref;
            }
        }

        Long restaurantId = SecurityUtils.currentRestaurantId();
        Instant fromTs = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toTs = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Order> orders = orderService.findDeliveredBetween(
                restaurantId, fromTs, toTs);

        Map<LocalDate, Acc> byDay = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, new Acc());
        }
        for (Order order : orders) {
            LocalDate day = order.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            Acc acc = byDay.get(day);
            if (acc == null) continue;
            acc.orders++;
            if (order.getTotalAmount() != null) acc.revenue = acc.revenue.add(order.getTotalAmount());
            if (order.getItems() != null) {
                for (OrderItem item : order.getItems()) {
                    BigDecimal unitCost = item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO;
                    acc.cost = acc.cost.add(unitCost.multiply(BigDecimal.valueOf(item.getQuantity())));
                }
            }
        }

        List<ProfitsResponse.DailyProfit> days = new ArrayList<>();
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        long count = 0;
        for (Map.Entry<LocalDate, Acc> entry : byDay.entrySet()) {
            Acc acc = entry.getValue();
            revenue = revenue.add(acc.revenue);
            cost = cost.add(acc.cost);
            count += acc.orders;
            days.add(new ProfitsResponse.DailyProfit(
                    entry.getKey(), acc.revenue, acc.cost,
                    acc.revenue.subtract(acc.cost), acc.orders));
        }
        return ProfitsResponse.from(normalized, from, to, revenue, cost, count, days);
    }

    private static class Acc {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        long orders = 0;
    }
}
