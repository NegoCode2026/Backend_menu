package com.menusaas.inventory.repository;

import com.menusaas.inventory.entity.RecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT p.id AS "productId", p.name AS "productName", i.id AS "ingredientId",
                   i.name AS "ingredientName", i.unit AS unit, i.stock_quantity AS stock,
                   i.low_stock_threshold AS threshold, r.quantity AS "perServing",
                   i.track_stock AS tracked,
                   CASE WHEN i.track_stock THEN greatest(0, floor(i.stock_quantity / r.quantity)) END AS servings
            FROM products p
            LEFT JOIN recipe_items r ON r.product_id = p.id
            LEFT JOIN ingredients i ON i.id = r.ingredient_id AND i.restaurant_id = p.restaurant_id
            WHERE p.restaurant_id = :restaurantId AND p.available
            ORDER BY p.name, p.id, i.name
            """, nativeQuery = true)
    List<com.menusaas.inventory.dto.RecipeCapacity> capacitiesForRestaurant(Long restaurantId);

    List<RecipeItem> findByProductId(Long productId);

    void deleteByProductId(Long productId);
}
