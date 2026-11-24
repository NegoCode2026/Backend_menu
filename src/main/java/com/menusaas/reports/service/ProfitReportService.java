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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Utilidades por pedidos ENTREGADOS (ingreso - costo snapshot).
 * period: day (1 día) | week (7 días) | month (mes calendario).
 * date: fecha de referencia (default hoy), zona del restaurante.
 */
@Service
@RequiredArgsConstructor
public class ProfitReportService {

    private final OrderService orderService;
    private final com.menusaas.restaurants.service.RestaurantService restaurants;

    @Transactional(readOnly = true)
    public ProfitsResponse getProfits(String period, LocalDate date) {
        String normalized = period == null ? "day" : period.trim().toLowerCase();
        if (!List.of("day", "week", "month").contains(normalized)) {
            throw new BadRequestException("period debe ser day|week|month");
        }
        Long restaurantId = SecurityUtils.currentRestaurantId();
        var zone = restaurants.businessZone(restaurantId);
        LocalDate ref = date != null ? date : LocalDate.now(zone);
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

        Instant fromTs = from.atStartOfDay(zone).toInstant();
        Instant toTs = to.plusDays(1).atStartOfDay(zone).toInstant();

        List<Order> orders = orderService.findDeliveredBetween(
                restaurantId, fromTs, toTs);

        Map<LocalDate, Acc> byDay = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, new Acc());
        }
        long soldItems = 0;
        Map<String, ProfitsResponse.ProductSales> products = new TreeMap<>();
        for (Order order : orders) {
            LocalDate day = (order.getDeliveredAt() != null ? order.getDeliveredAt() : order.getCreatedAt()).atZone(zone).toLocalDate();
            Acc acc = byDay.get(day);
            if (acc == null) continue;
            acc.orders++;
            if (order.getTotalAmount() != null) acc.revenue = acc.revenue.add(order.getTotalAmount());
            if (order.getItems() != null) {
                for (OrderItem item : order.getItems()) {
                    soldItems += item.getQuantity();
                    products.compute(item.getProductName(), (name, previous) -> new ProfitsResponse.ProductSales(
                            name, item.getQuantity() + (previous == null ? 0 : previous.qty()),
                            item.getSubtotal().add(previous == null ? BigDecimal.ZERO : previous.revenue())));
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
        return ProfitsResponse.from(normalized, from, to, revenue, cost, count, days, soldItems, products.values().stream()
                .sorted(java.util.Comparator.comparingLong(ProfitsResponse.ProductSales::qty).reversed())
                .limit(5).toList());
    }

    private static class Acc {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        long orders = 0;
    }
}
