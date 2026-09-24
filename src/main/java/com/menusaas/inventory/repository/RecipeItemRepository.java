package com.menusaas.inventory.repository;

import com.menusaas.inventory.entity.RecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {

    List<RecipeItem> findByProductId(Long productId);

    void deleteByProductId(Long productId);
}
