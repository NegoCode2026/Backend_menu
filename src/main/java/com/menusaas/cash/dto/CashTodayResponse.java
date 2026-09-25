package com.menusaas.cash.dto;

import com.menusaas.orders.dto.OrderResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CashTodayResponse(
        LocalDate date,
        BigDecimal expectedCash,
        BigDecimal expectedCard,
        BigDecimal expectedTransfer,
        BigDecimal expectedTotal,
        long deliveredOrders,
        long unpaidDelivered,
        /** Pedidos entregados del día que todavía no tienen método de pago. */
        List<OrderResponse> unpaidOrders,
        /** Pedidos del día ya cobrados (con método y momento de pago). */
        List<OrderResponse> paidOrders,
        CashClosingResponse closing
) {
    public static CashTodayResponse from(
            LocalDate date, BigDecimal expectedCash, BigDecimal expectedCard,
            BigDecimal expectedTransfer, long deliveredOrders, long unpaidDelivered,
            List<OrderResponse> unpaidOrders, List<OrderResponse> paidOrders, CashClosingResponse closing) {
        return new CashTodayResponse(
                date, expectedCash, expectedCard, expectedTransfer,
                expectedCash.add(expectedCard).add(expectedTransfer),
                deliveredOrders, unpaidDelivered, unpaidOrders, paidOrders, closing);
    }
}
