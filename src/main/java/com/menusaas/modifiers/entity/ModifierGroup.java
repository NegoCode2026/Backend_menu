package com.menusaas.modifiers.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Grupo de opciones de un producto: "Término", "Tamaño", "Extras".
 *
 * <p>min/max y required definen el contrato de selección. "Término" sería
 * obligatorio con exactamente 1 opción; "Extras", opcional de 0 a 3.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "modifier_groups")
public class ModifierGroup implements com.menusaas.shared.tenancy.TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "min_selections", nullable = false)
    @Builder.Default
    private int minSelections = 0;

    @Column(name = "max_selections", nullable = false)
    @Builder.Default
    private int maxSelections = 1;

    @Column(nullable = false)
    @Builder.Default
    private boolean required = false;

    @Column(nullable = false)
    @Builder.Default
    private int position = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
