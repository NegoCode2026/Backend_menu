package com.menusaas.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProfitsResponse(
        String period,
        LocalDate from,
        LocalDate to,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit,
        long orders,
        List<DailyProfit> days,
        long soldItems,
        List<ProductSales> topProducts
) {
    public static ProfitsResponse from(
            String period, LocalDate from, LocalDate to,
            BigDecimal revenue, BigDecimal cost, long orders, List<DailyProfit> days, long soldItems, List<ProductSales> topProducts) {
        return new ProfitsResponse(
                period, from, to, revenue, cost, revenue.subtract(cost), orders, days, soldItems, topProducts);
    }

    public record ProductSales(String name, long qty, BigDecimal revenue) {}

    public record DailyProfit(
            LocalDate date,
            BigDecimal revenue,
            BigDecimal cost,
            BigDecimal profit,
            long orders
    ) {
    }
}
