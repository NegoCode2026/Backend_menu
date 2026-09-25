package com.menusaas.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "recipe_items")
public class RecipeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "ingredient_id", nullable = false)
    private Long ingredientId;

    /** Cantidad del ingrediente por UNA unidad del plato. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;
}
