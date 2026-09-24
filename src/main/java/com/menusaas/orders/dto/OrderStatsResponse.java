package com.menusaas.orders.dto;

import java.math.BigDecimal;

public record OrderStatsResponse(
        long total,
        long pending,
        long confirmed,
        long inPreparation,
        long ready,
        long delivered,
        long cancelled,
        long todayCount,
        BigDecimal todayRevenue
) {
    public static OrderStatsResponse from(
            long total, long pending, long confirmed, long inPreparation, long ready,
            long delivered, long cancelled, long todayCount, BigDecimal todayRevenue) {
        return new OrderStatsResponse(
                total, pending, confirmed, inPreparation, ready,
                delivered, cancelled, todayCount, todayRevenue);
    }
}