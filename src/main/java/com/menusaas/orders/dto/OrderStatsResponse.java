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
}