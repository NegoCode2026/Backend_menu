package com.menusaas.cash.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cash_closings")
public class CashClosing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    /** Suma de entregados en efectivo ese día (calculado al cerrar). */
    @Column(name = "expected_cash", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal expectedCash = BigDecimal.ZERO;

    /** Efectivo contado en caja por el cajero. */
    @Column(name = "counted_cash", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal countedCash = BigDecimal.ZERO;

    /** counted - expected (negativo = faltante). */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal difference = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "closed_by")
    private Long closedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
