package com.menusaas.cash.dto;

import com.menusaas.cash.entity.CashClosing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CashClosingResponse(
        Long id,
        LocalDate businessDate,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal difference,
        String notes,
        Long closedBy,
        Instant createdAt
) {
    public static CashClosingResponse from(CashClosing c) {
        return new CashClosingResponse(
                c.getId(), c.getBusinessDate(), c.getExpectedCash(), c.getCountedCash(),
                c.getDifference(), c.getNotes(), c.getClosedBy(), c.getCreatedAt()
        );
    }
}
