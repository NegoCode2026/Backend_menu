package com.menusaas.products.dto;

import com.menusaas.products.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        Long restaurantId,
        Long categoryId,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean available,
        int position,
        BigDecimal costPrice,
        int stockQuantity,
        int lowStockThreshold,
        boolean trackStock,
        boolean lowStock,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * @param imageUrl URL ya resuelta por el llamador (firmada o null).
     */
    public static ProductResponse from(Product p, String imageUrl) {
        return new ProductResponse(
                p.getId(), p.getRestaurantId(), p.getCategoryId(), p.getName(), p.getDescription(),
                p.getPrice(), imageUrl, p.isAvailable(), p.getPosition(),
                p.getCostPrice(), p.getStockQuantity(), p.getLowStockThreshold(), p.isTrackStock(),
                p.isTrackStock() && p.getStockQuantity() <= p.getLowStockThreshold(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
