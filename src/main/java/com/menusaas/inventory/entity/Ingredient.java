package com.menusaas.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ingredients")
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 160)
    private String name;

    /** Unidad de medida: und, g, kg, ml, l... */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String unit = "und";

    @Column(name = "stock_quantity", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal stockQuantity = BigDecimal.ZERO;

    @Column(name = "low_stock_threshold", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal lowStockThreshold = new BigDecimal("5");

    @Column(name = "track_stock", nullable = false)
    @Builder.Default
    private boolean trackStock = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
