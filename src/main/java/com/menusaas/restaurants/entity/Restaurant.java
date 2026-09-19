package com.menusaas.restaurants.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "restaurants")
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "logo_url", columnDefinition = "text")
    private String logoUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(length = 30)
    private String whatsapp;

    @Column(length = 120)
    private String instagram;

    @Column(length = 120)
    private String facebook;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Interruptor de servicio: el dueño lo apaga para dejar de recibir
     * pedidos (cerrado). No afecta la visibilidad del menu publico.
     */
    @Builder.Default
    @Column(name = "open", nullable = false)
    private boolean open = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}