package com.menusaas.tables.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "restaurant_tables")
public class RestaurantTable implements com.menusaas.shared.tenancy.TenantOwned {

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

    @Column(nullable = false, length = 20)
    private String number;

    @Column(nullable = false)
    private int seats = 2;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
