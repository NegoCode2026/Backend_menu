package com.menusaas.subscriptions.dto;

import com.menusaas.subscriptions.entity.Plan;

import java.math.BigDecimal;

public record PlanResponse(
        Long id,
        String code,
        String name,
        String description,
        BigDecimal priceMonthly
) {
    public static PlanResponse from(Plan p) {
        return new PlanResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getPriceMonthly());
    }
}