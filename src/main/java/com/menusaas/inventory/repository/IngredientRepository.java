package com.menusaas.inventory.repository;

import com.menusaas.inventory.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    List<Ingredient> findByRestaurantIdOrderByNameAsc(Long restaurantId);

    Optional<Ingredient> findByIdAndRestaurantId(Long id, Long restaurantId);
}
