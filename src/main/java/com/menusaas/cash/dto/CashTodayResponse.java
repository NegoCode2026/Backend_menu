package com.menusaas.cash.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashTodayResponse(
        LocalDate date,
        BigDecimal expectedCash,
        BigDecimal expectedCard,
        BigDecimal expectedTransfer,
        BigDecimal expectedTotal,
        long deliveredOrders,
        long unpaidDelivered,
        CashClosingResponse closing
) {
}
