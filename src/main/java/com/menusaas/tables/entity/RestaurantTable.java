package com.menusaas.tables.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Mesa física del restaurante (MVP esencial).
 *
 * <p>El {@code code} UUID único global es lo que viaja en el QR
 * ({@code /menu/{slug}?t={code}}). El backend lo valida para saber la mesa
 * real y el restaurante al que pertenece. La relación con el restaurante es
 * la FK {@code restaurant_id} (ids escalares por convención); la información
 * del restaurante se resuelve vía {@code RestaurantService}, nunca con
 * {@code @ManyToOne}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "restaurant_tables",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tables_restaurant_label",
                        columnNames = {"restaurant_id", "label"})
        })
public class RestaurantTable implements com.menusaas.shared.tenancy.TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 30)
    private String label;

    @Column(nullable = false, unique = true, columnDefinition = "uuid")
    private UUID code;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
