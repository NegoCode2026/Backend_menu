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
        List<DailyProfit> days
) {
    public record DailyProfit(
            LocalDate date,
            BigDecimal revenue,
            BigDecimal cost,
            BigDecimal profit,
            long orders
    ) {
    }
}
