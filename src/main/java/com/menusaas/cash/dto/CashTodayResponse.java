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
    public static CashTodayResponse from(
            LocalDate date, BigDecimal expectedCash, BigDecimal expectedCard,
            BigDecimal expectedTransfer, long deliveredOrders, long unpaidDelivered,
            CashClosingResponse closing) {
        return new CashTodayResponse(
                date, expectedCash, expectedCard, expectedTransfer,
                expectedCash.add(expectedCard).add(expectedTransfer),
                deliveredOrders, unpaidDelivered, closing);
    }
}
