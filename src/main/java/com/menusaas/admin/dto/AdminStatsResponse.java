package com.menusaas.admin.dto;

public record AdminStatsResponse(
        long totalRestaurants,
        long activeRestaurants,
        long totalUsers,
        long activeSubscriptions,
        long totalProducts
) {
    public static AdminStatsResponse from(
            long totalRestaurants, long activeRestaurants, long totalUsers,
            long activeSubscriptions, long totalProducts) {
        return new AdminStatsResponse(
                totalRestaurants, activeRestaurants, totalUsers, activeSubscriptions, totalProducts);
    }
}
