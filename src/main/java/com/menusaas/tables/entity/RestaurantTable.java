package com.menusaas.tables.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "restaurant_tables", uniqueConstraints = {
        @UniqueConstraint(name = "uk_restaurant_tables_number", columnNames = {"restaurant_id", "table_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "table_number", nullable = false, length = 50)
    private String number;

    @Column(nullable = false)
    @Builder.Default
    private Integer seats = 2;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
