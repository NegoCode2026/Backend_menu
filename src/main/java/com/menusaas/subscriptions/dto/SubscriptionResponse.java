package com.menusaas.subscriptions.dto;

import com.menusaas.subscriptions.entity.Subscription;

import java.time.Instant;

public record SubscriptionResponse(
        Long id,
        Long restaurantId,
        PlanResponse plan,
        String status,
        String provider,
        String providerReference,
        Instant startsAt,
        Instant endsAt
) {
    public static SubscriptionResponse from(Subscription s, PlanResponse plan) {
        return new SubscriptionResponse(
                s.getId(), s.getRestaurantId(), plan,
                s.getStatus(), s.getProvider(), s.getProviderReference(),
                s.getStartsAt(), s.getEndsAt()
        );
    }
}