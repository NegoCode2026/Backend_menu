package com.menusaas.modifiers.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Opción que eligió el cliente, con el precio CONGELADO en el momento del pedido.
 *
 * <p>No se guarda solo el id a propósito: order_items.product_id es ON DELETE
 * SET NULL, y el restaurante puede cambiar precios o borrar una opción. Si el
 * pedido guardara solo la referencia, un pedido viejo dejaría de cuadrar. Aquí se
 * guarda el nombre y el delta tal como estaban.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_item_modifiers")
public class OrderItemModifier implements com.menusaas.shared.tenancy.TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "group_name", nullable = false, length = 120)
    private String groupName;

    @Column(name = "modifier_name", nullable = false, length = 120)
    private String modifierName;

    @Column(name = "price_delta", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal priceDelta = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
