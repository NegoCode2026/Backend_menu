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
public class Ingredient implements com.menusaas.shared.tenancy.TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Versionado optimista. Sin esto, dos ediciones simultaneas (dos admins, o
     * la misma persona en dos pestanas) se pisan en silencio: el segundo UPDATE
     * sobrescribe al primero sin error ni aviso. Para precios y costes eso es
     * perdida de dinero sin rastro.
     */
    @Version
    @Column(nullable = false)
    private Long version;

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

    /** Precio de compra por unidad (para costear recetas). */
    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;

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
